/*
 * Copyright 2015 Kevin Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.airk.trigger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Trigger loop, all jobs finally get in here and be managed, checked and executed.
 */
public final class TriggerLoop extends Service {
    static final String CONDITION_DATA = "condition_data";
    static final String PROTOCOL_KEY = "protocol_key";
    static final String DEVICE_KEY = "device_key";
    static final String STATUS_CHANGED = "status_changed";
    static final int PROTOCOL_CODE = 0x991;
    static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2 + 1;

    private static final String TAG = "TriggerLoop";
    private static final String DEADLINE_BROADCAST = "com.github.airk.trigger.broadcast.deadline";
    private static final String JOB_PERSIST_DIR = "job_persist";
    private static final String JOB_BACKUP_DIR = "job_backup";

    private ConcurrentHashMap<String, Job> jobSet;
    private ConcurrentHashMap<String, BroadcastReceiver> receivers;
    private ConcurrentHashMap<String, Long> jobHappens;
    private TriggerBinder binder;
    private CheckHandler checker;
    private ExecutorService executor;
    private Handler mainHandler;
    private AlarmManager alarmManager;
    private Handler shortDeadlineHandler;
    private DeadlineCheck deadlineCheck;
    private PowerManager.WakeLock wakeLock;
    private DeviceStatus sDeviceStatus;
    private HandlerThread handlerThread;

    static Intent newIntent(Context context) {
        Intent intent = new Intent(context, TriggerLoop.class);
        intent.putExtra(PROTOCOL_KEY, PROTOCOL_CODE);
        return intent;
    }

    static Intent newIntent(Context context, ConditionDesc condition) {
        Intent data = newIntent(context);
        data.putExtra(CONDITION_DATA, condition);
        return data;
    }

    static Intent deviceOn(Context context) {
        Intent data = newIntent(context);
        data.putExtra(DEVICE_KEY, true);
        return data;
    }

    static Intent deviceStatusChanged(Context context, String which) {
        Intent data = newIntent(context);
        data.putExtra(STATUS_CHANGED, which);
        return data;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        jobSet = new ConcurrentHashMap<>();
        receivers = new ConcurrentHashMap<>();
        jobHappens = new ConcurrentHashMap<>();
        binder = new TriggerBinder();
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new TriggerWorkerFactory());
        mainHandler = new Handler(Looper.getMainLooper());
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        shortDeadlineHandler = new Handler();
        deadlineCheck = new DeadlineCheck();
        sDeviceStatus = DeviceStatus.get(this);
        registerReceiver(deadlineCheck, new IntentFilter(DEADLINE_BROADCAST));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        int granted = checkCallingOrSelfPermission("android.permission.WAKE_LOCK");
        if (granted == PackageManager.PERMISSION_GRANTED) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        } else {
            wakeLock = null;
        }
        handlerThread = new HandlerThread("Trigger-HandlerThread");
        handlerThread.start();
        checker = new CheckHandler(handlerThread.getLooper());
        mayRecoverJobsFromFile();
    }

    private void mayRecoverJobsFromFile() {
        File recoverDir = new File(getFilesDir(), JOB_BACKUP_DIR);
        if (!recoverDir.exists())
            return;
        if (recoverDir.listFiles() == null)
            return;
        for (File file : recoverDir.listFiles()) {
            Job job = null;
            try {
                job = Job.createJobFromPersistInfo(Job.JobInfo.readFromFile(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
            file.delete();
            if (job != null) {
                addJob(job, true);
            }
        }
    }

    private void tryCreateBackup(Job job) {
        if (!job.canBePersist)
            return;
        File recoverDir = new File(getFilesDir(), JOB_BACKUP_DIR);
        if (!recoverDir.exists()) {
            recoverDir.mkdirs();
        }
        try {
            job.jobInfo.writeToFile(recoverDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //try to persist job that need valid after reboot
        if (!job.jobInfo.persistAfterReboot)
            return;
        final File dir = new File(getFilesDir(), JOB_PERSIST_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            job.jobInfo.writeToFile(dir);
        } catch (IOException ignore) {
        }
    }

    private void deleteBackup(Job job) {
        File recoverDir = new File(getFilesDir(), JOB_BACKUP_DIR);
        if (!recoverDir.exists())
            return;
        job.jobInfo.tryDelete(recoverDir);
        final File dir = new File(getFilesDir(), JOB_PERSIST_DIR);
        if (!dir.exists()) {
            return;
        }
        if (!job.jobInfo.persistAfterReboot)
            return;
        job.jobInfo.tryDelete(dir);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sDeviceStatus.onDestroy();
        handlerThread.quit();
        unregisterReceiver(deadlineCheck);
    }

    private void tryAcquireLock() {
        if (wakeLock != null) {
            wakeLock.acquire();
        }
    }

    private void tryReleaseLock() {
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    void addJob(Job job, boolean mayTrigger) {
        if (job == null)
            return;
        if (jobSet.containsKey(job.jobInfo.identity))
            return;
        //avoid duplicate job
        if (job.jobInfo.persistAfterReboot) {
            for (String jk : jobSet.keySet()) {
                Job existJob = jobSet.get(jk);
                if (existJob.equals(job))
                    return;
            }
        }
        jobSet.put(job.jobInfo.identity, job);
        tryCreateBackup(job);
        //receiver handle
        for (Condition c : job.exConds) {
            if (!receivers.containsKey(c.getIdentify())) {
                IntentFilter filter = new IntentFilter();
                for (String act : c.getAction()) {
                    filter.addAction(act);
                }
                ReceiverInner rec = new ReceiverInner(c);
                registerReceiver(rec, filter);
                receivers.put(c.getIdentify(), rec);
            }
        }
        //can it happen now?
        if (mayTrigger) {
            if (job.condSatisfied.containsKey(Job.CHARGING_KEY)) {
                job.condSatisfied.put(Job.CHARGING_KEY, DeviceStatus.chargingConstraintSatisfied.get());
            }
            if (job.condSatisfied.containsKey(Job.NETWORK_TYPE_KEY)) {
                job.condSatisfied.put(Job.NETWORK_TYPE_KEY, DeviceStatus.networkTypeSatisfied(job.jobInfo.networkType));
            }
            if (job.condSatisfied.containsKey(Job.IDLE_DEVICE_KEY)) {
                job.condSatisfied.put(Job.IDLE_DEVICE_KEY, DeviceStatus.idleConstraintSatisfied.get());
            }
            if (checker.mayTriggerAfterCheck(job))
                return;
        }

        //deadline handle
        if (job.jobInfo.deadline != -1L) {
            long df = job.jobInfo.deadline - System.currentTimeMillis();
            if (df < 0) {
                checker.trigger(job);
                return;
            }
            if (df > 60 * 1000) {
                PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(DEADLINE_BROADCAST), 0);
                if (Build.VERSION.SDK_INT >= 19) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, job.jobInfo.deadline, pi);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, job.jobInfo.deadline, pi);
                }
                job.deadLineObj = pi;
            } else {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        checker.checkDeadline();
                    }
                };
                job.deadLineObj = r;
                shortDeadlineHandler.postDelayed(r, df);
            }
        }
    }

    void removeJob(String tag) {
        ArrayList<String> tobeRemoved = new ArrayList<>();
        for (String key : jobSet.keySet()) {
            Job job = jobSet.get(key);
            if (job.jobInfo.tag.equals(tag)) {
                tobeRemoved.add(key);
            }
        }
        for (String k : tobeRemoved) {
            removeOne(k);
        }
    }

    void removeOne(String key) {
        Job removed = null;
        if (jobSet.containsKey(key)) {
            removed = jobSet.remove(key);
        }
        if (removed == null)
            return;
        deleteBackup(removed);
        List<String> removedConds = new ArrayList<>();
        for (Receiver c : removed.exConds) {
            removedConds.add(c.getIdentify());
        }
        for (Map.Entry<String, Job> entry : jobSet.entrySet()) {
            for (Receiver c : entry.getValue().exConds) {
                if (removedConds.indexOf(c.getIdentify()) != -1) {
                    removedConds.remove(c.getIdentify());
                }
            }
        }
        for (String k : removedConds) {
            unregisterReceiver(receivers.get(k));
            receivers.remove(k);
        }
        //deadline handle
        if (removed.jobInfo.deadline != -1L) {
            if (removed.deadLineObj instanceof PendingIntent) {
                alarmManager.cancel((PendingIntent) removed.deadLineObj);
            } else if (removed.deadLineObj instanceof Runnable) {
                shortDeadlineHandler.removeCallbacks((Runnable) removed.deadLineObj);
            }
        }
    }

    void cleanUpAll() {
        checker.cleanup();
        for (Map.Entry<String, Job> entry : jobSet.entrySet()) {
            Job job = entry.getValue();
            job.resetConds();
            if (job.deadLineObj != null) {
                if (job.deadLineObj instanceof PendingIntent) {
                    alarmManager.cancel((PendingIntent) job.deadLineObj);
                } else if (job.deadLineObj instanceof Runnable) {
                    shortDeadlineHandler.removeCallbacks((Runnable) job.deadLineObj);
                }
            }
        }
        jobSet.clear();
        for (Map.Entry<String, BroadcastReceiver> entry : receivers.entrySet()) {
            BroadcastReceiver r = entry.getValue();
            unregisterReceiver(r);
        }
        receivers.clear();
        deleteDir(new File(getFilesDir(), JOB_BACKUP_DIR));
        deleteDir(new File(getFilesDir(), JOB_PERSIST_DIR));
    }

    private void deleteDir(File file) {
        if (!file.exists() || !file.isDirectory()) {
            return;
        }
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                deleteDir(f);
            } else {
                f.delete();
            }
        }
        file.delete();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        if (!intent.hasExtra(PROTOCOL_KEY) || intent.getIntExtra(PROTOCOL_KEY, -1) != PROTOCOL_CODE) {
            throw new IllegalAccessError("TriggerLoop won't receive user command.");
        }
        if (intent.hasExtra(CONDITION_DATA)) {
            ConditionDesc condition = intent.getParcelableExtra(CONDITION_DATA);
            Log.d(TAG, condition.toString());
            checker.checkSatisfy(condition);
        } else if (intent.hasExtra(DEVICE_KEY)) {
            checker.checkDeviceOn();
        } else if (intent.hasExtra(STATUS_CHANGED)) {
            checker.checkStatusChanged(intent.getStringExtra(STATUS_CHANGED));
        }
        return START_STICKY;
    }

    private static class TriggerWorkerFactory implements ThreadFactory {
        static int counter = 0;

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Trigger-Worker:" + ++counter);
        }

    }

    protected class TriggerBinder extends Binder {
        void schedule(Job job) {
            addJob(job, true);
        }

        void cancel(String tag) {
            removeJob(tag);
        }

        void removePersistJob(String tag) {
            checker.removePersistJobWithTag(tag);
        }

        void stopAndReset() {
            cleanUpAll();
        }

    }

    private class CheckHandler extends Handler {
        private final int MSG_SATISFY = 1;
        private final int MSG_DEADLINE = 2;
        private final int MSG_DEVICE_ON = 3;
        private final int MSG_STATUS_CHANGED = 4;
        private final int MSG_REMOVE_TAG_JOB = 5;

        public CheckHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SATISFY:
                    checkSatisfyImpl((ConditionDesc) msg.obj);
                    break;
                case MSG_DEADLINE:
                    checkDeadlineImpl();
                    break;
                case MSG_DEVICE_ON:
                    checkDeviceOnImpl();
                    break;
                case MSG_STATUS_CHANGED:
                    checkStatusChangedImpl((String) msg.obj);
                    break;
                case MSG_REMOVE_TAG_JOB:
                    removePersistJobWithTagImpl((String) msg.obj);
                    break;
                default:
                    break;
            }
        }

        private void checkSatisfyImpl(ConditionDesc cond) {
            tryAcquireLock();
            for (Map.Entry<String, Job> entry : jobSet.entrySet()) {
                final Job job = entry.getValue();
                boolean hit = true;
                for (String key : job.condSatisfied.keySet()) {
                    if (key.equals(cond.ident)) {
                        job.condSatisfied.put(key, cond.satisfy);
                    }
                    if (hit) {
                        hit = job.condSatisfied.get(key);
                    }
                }
                if (hit) {
                    trigger(job);
                }
            }
            tryReleaseLock();
        }

        private void checkStatusChangedImpl(final String which) {
            tryAcquireLock();
            //while code runs here, all status have been refreshed, we just check all jobs here and
            //try to find out which can be triggered.
            for (String key : jobSet.keySet()) {
                Job job = jobSet.get(key);
                switch (which) {
                    case Job.CHARGING_KEY:
                        if (job.condSatisfied.containsKey(Job.CHARGING_KEY)) {
                            job.condSatisfied.put(Job.CHARGING_KEY,
                                    DeviceStatus.chargingConstraintSatisfied.get());
                        }
                        break;
                    case Job.IDLE_DEVICE_KEY:
                        if (job.condSatisfied.containsKey(Job.IDLE_DEVICE_KEY)) {
                            job.condSatisfied.put(Job.IDLE_DEVICE_KEY,
                                    DeviceStatus.idleConstraintSatisfied.get());
                        }
                        break;
                    case Job.NETWORK_TYPE_KEY:
                        if (job.condSatisfied.containsKey(Job.NETWORK_TYPE_KEY)) {
                            job.condSatisfied.put(Job.NETWORK_TYPE_KEY,
                                    DeviceStatus.networkTypeSatisfied(job.jobInfo.networkType));
                        }
                        break;
                    default:
                        break;
                }
                if (job.condSatisfied.containsKey(which)) {
                    mayTriggerAfterCheck(job);
                }
            }
            tryReleaseLock();
        }

        private void checkDeadlineImpl() {
            tryAcquireLock();
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Job> entry : jobSet.entrySet()) {
                final Job job = entry.getValue();
                long happen = jobHappens.get(job.jobInfo.identity) == null ? -1 : jobHappens.get(job.jobInfo.identity);
                if (happen == -1L && (job.jobInfo.deadline > 0 && job.jobInfo.deadline <= now)) { //not happen yet
                    trigger(job);
                }
            }
            tryReleaseLock();
        }

        private void checkDeviceOnImpl() {
            tryAcquireLock();
            final File dir = new File(getFilesDir(), JOB_PERSIST_DIR);
            if (dir.listFiles() == null)
                return;
            for (File f : dir.listFiles()) {
                if (!f.getName().startsWith("1553")) {
                    f.delete();
                    continue;
                }
                Job.JobInfo info = null;
                try {
                    info = Job.JobInfo.readFromFile(f);
                } catch (IOException ignore) {
                }
                if (info != null) {
                    Job job = Job.createJobFromPersistInfo(info);
                    if (job != null) {
                        jobHappens.remove(job.jobInfo.identity);
                        addJob(job, true);
                    }
                } else {
                    f.delete();
                }
            }
            tryReleaseLock();
        }

        private void removePersistJobWithTagImpl(String tag) {
            File backupDir = new File(getFilesDir(), JOB_BACKUP_DIR);
            File persistDir = new File(getFilesDir(), JOB_PERSIST_DIR);
            if (backupDir.exists() && backupDir.listFiles() != null) {
                for (File f : backupDir.listFiles()) {
                    try {
                        Job.JobInfo info = Job.JobInfo.readFromFile(f);
                        if (info != null && info.tag.equals(tag)) {
                            f.delete();
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
            if (persistDir.exists() && persistDir.listFiles() != null) {
                for (File f : persistDir.listFiles()) {
                    try {
                        Job.JobInfo info = Job.JobInfo.readFromFile(f);
                        if (info != null && info.tag.equals(tag)) {
                            f.delete();
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        boolean mayTriggerAfterCheck(final Job job) {
            boolean hit = false;
            for (String key : job.condSatisfied.keySet()) {
                if (job.condSatisfied.get(key)) {
                    hit = true;
                } else {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                trigger(job);
            }
            return hit;
        }

        void trigger(final Job job) {
            Log.d(TAG, "trigger() " + job.jobInfo.identity);
            long happen = jobHappens.get(job.jobInfo.identity) == null ? -1 : jobHappens.get(job.jobInfo.identity);
            if (happen != -1 && job.jobInfo.delay != -1) {
                if (SystemClock.elapsedRealtime() - happen < job.jobInfo.delay) {
                    Log.d(TAG, "trigger: Delay hit, last is " + happen);
                    return;
                }
            }
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    Act act = job.action;
                    if (act instanceof Action) {
                        ((Action) act).act();
                    } else if (act instanceof ContextAction) {
                        ((ContextAction) act).act(TriggerLoop.this);
                    }
                }
            };
            if (job.jobInfo.threadSpace == ThreadSpace.MAIN) {
                mainHandler.post(r);
            } else {
                executor.submit(r);
            }
            jobHappens.put(job.jobInfo.identity, SystemClock.elapsedRealtime());
            removeOne(job.jobInfo.identity);
            job.resetConds();
            if (job.jobInfo.repeat) {
                addJob(job, false);
            }
        }


        public void checkSatisfy(ConditionDesc cond) {
            obtainMessage(MSG_SATISFY, cond).sendToTarget();
        }

        public void checkDeadline() {
            sendEmptyMessage(MSG_DEADLINE);
        }

        public void checkDeviceOn() {
            sendEmptyMessage(MSG_DEVICE_ON);
        }

        public void checkStatusChanged(String which) {
            obtainMessage(MSG_STATUS_CHANGED, which).sendToTarget();
        }

        public void removePersistJobWithTag(String tag) {
            obtainMessage(MSG_REMOVE_TAG_JOB, tag).sendToTarget();
        }

        public void cleanup() {
            removeCallbacksAndMessages(null);
        }
    }

    private class DeadlineCheck extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            checker.checkDeadline();
        }
    }

    private class ReceiverInner extends BroadcastReceiver {
        private final Receiver receiver;

        private ReceiverInner(Receiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            receiver.onReceive(context, intent);
        }
    }

}
