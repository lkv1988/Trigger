package com.github.airk.triggertest;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.github.airk.trigger.Action;
import com.github.airk.trigger.Condition;
import com.github.airk.trigger.ContextAction;
import com.github.airk.trigger.PersistReceiver;
import com.github.airk.trigger.Job;
import com.github.airk.trigger.ThreadSpace;
import com.github.airk.trigger.TimeoutLatch;
import com.github.airk.trigger.Trigger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by kevin on 15/4/23.
 */
public class TestTriggerActivity extends TestBaseActivity {
    static final String COND1 = "trigger.testcase.cond1";
    static final String COND2 = "trigger.testcase.cond2";
    static final String COND3 = "trigger.testcase.cond3";
    private static final String TAG = "TriggerTest";
    static AtomicInteger actCounter;
    static TimeoutLatch timeoutLatch;
    static PowerManager.WakeLock lock;
    final int AWAIT_TIME = 500;
    PersistReceiver deviceReceiver;

    static final String DEBUG_ON_BROADCAST = "trigger.testcase.deviceon";
    static final String DEBUG_OFF_BROADCAST = "trigger.testcase.deviceoff";

    @SetUp
    void setUp(String name) {
        Log.d(TAG, "setUp --- " + name);
        actCounter = new AtomicInteger(0);
        assertNotNull(trigger);
        try {
            Field f = trigger.getClass().getDeclaredField("triggerBinder");
            f.setAccessible(true);
            Object binder = f.get(trigger);
            assertNotNull(binder);
        } catch (Exception ignore) {
            assertEquals(1, 2);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        deviceReceiver = new PersistReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DEBUG_ON_BROADCAST);
        filter.addAction(DEBUG_OFF_BROADCAST);
        registerReceiver(deviceReceiver, filter);
    }

    @TearDown
    void tearDown() {
        Log.d(TAG, "tearDown");
        unregisterReceiver(deviceReceiver);
        deviceReceiver = null;
        actCounter = null;
        timeoutLatch = null;
        trigger.stopAndReset();
        File file = new File(getFilesDir(), "job_persist");
        deleteDir(file);
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

    @Test
    void testIllegalClassType() {
        try {
            Job job = new Job(new Action() {
                @Override
                protected void act() {
                    //no-op
                }
            }).withExtra(new Condition() {
                @Override
                public String[] getAction() {
                    return new String[]{COND1};
                }
            });
            assertEquals(1, 1);
        } catch (IllegalArgumentException ignore) {
            assertEquals(1, 2);
        }

        try {
            Job job = new Job(true, new ActDummy())
                    .withExtra(new PersistCond1());
            assertEquals(1, 2);
        } catch (IllegalArgumentException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test
    void testTimeoutLatch() {
        final TimeoutLatch latch = new TimeoutLatch(1);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 10);
        try {
            latch.await(AWAIT_TIME);
            assertEquals(1, 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }

        final TimeoutLatch latch1 = new TimeoutLatch(1);
        try {
            latch1.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test
    void testIllegalJob() {
        Job job = new Job(new ActDummy());
        try {
            trigger.schedule(job);
            assertEquals(1, 2);
        } catch (IllegalArgumentException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test
    void testTrigger1with1() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act());
        job.withExtra(new Cond1());
        trigger.schedule(job);

        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test
    void testTrigger2with1() {
        timeoutLatch = new TimeoutLatch(2);

        Job job1 = new Job(new Act())
                .withExtra(new Cond1());
        Job job2 = new Job(new Act())
                .withExtra(new Cond1());
        trigger.schedule(job1, job2);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 2);
        } catch (TimeoutException e) {
            e.printStackTrace();
            assertEquals(1, 2);
        }
    }

    @Test
    void testTrigger2with2() {
        timeoutLatch = new TimeoutLatch(2);
        Job job1 = new Job(new Act())
                .withExtra(new Cond1());
        Job job2 = new Job(new Act())
                .withExtra(new Cond2());
        trigger.schedule(job1, job2);
        sendBroadcastDelay(COND1, COND2);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 2);
        } catch (TimeoutException e) {
            e.printStackTrace();
            assertEquals(1, 2);
        }
    }

    @Test
    void testTrigger1with2() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .withExtra(new Cond1())
                .withExtra(new Cond2());
        trigger.schedule(job);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException e) {
            assertEquals(1, 1);
        }

        sendBroadcastDelay(COND2);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException e) {
            e.printStackTrace();
            assertEquals(1, 2);
        }
    }

    private void sendBroadcastDelay(final String... action) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                for (String act : action) {
                    sendBroadcast(new Intent(act));
                }
            }
        }, 3);
    }

    @Test
    void testRepeatJob() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .repeat()
                .withExtra(new Cond1());
        trigger.schedule(job);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 3);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test
    void testRepeatDelayJob() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .repeat(AWAIT_TIME + 100)
                .withExtra(new Cond3());
        trigger.schedule(job);
        sendBroadcastDelay(COND3);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND3);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test
    void testDeadlineJob() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .withExtra(new Cond3())
                .deadline(System.currentTimeMillis() + AWAIT_TIME / 2);
        trigger.schedule(job);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test
    void testMultiCondsJob() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .withExtra(new Cond1())
                .withExtra(new Cond2())
                .withExtra(new Cond3());
        trigger.schedule(job);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
        sendBroadcastDelay(COND2);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
        sendBroadcastDelay(COND3);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test
    void testMultiJobAndConds() {
        timeoutLatch = new TimeoutLatch(1);
        Job job1 = new Job(new Act())
                .withExtra(new Cond1());

        Job job2 = new Job(new Act())
                .withExtra(new Cond1())
                .withExtra(new Cond2());

        Job job3 = new Job(new Act())
                .withExtra(new Cond1())
                .withExtra(new Cond2())
                .withExtra(new Cond3());
        trigger.schedule(job1, job2, job3);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND2);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND3);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 3);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }

        //test illegal trigger
        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND3);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test
    void testActWithWakelock() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new LockAct())
                .withExtra(new Cond1())
                .deadline(System.currentTimeMillis() + AWAIT_TIME / 2);
        trigger.schedule(job);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test
    void testThreadSpace() {
        timeoutLatch = new TimeoutLatch(3);
        Job underJob = new Job(new UnderAct())
                .withExtra(new Cond1());
        Job underJob2 = new Job(new UnderAct())
                .attachOn(ThreadSpace.BACKGROUND)
                .withExtra(new Cond1());
        Job mainJob = new Job(new MainAct())
                .attachOn(ThreadSpace.MAIN)
                .withExtra(new Cond1());
        trigger.schedule(underJob, underJob2, mainJob);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 3);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

//    need trigger inner debug broadcast enable
    @Test
    void testDeviceOffAndOn() {
        Job job1 = new Job(true, new PersistAct())
                .withExtra(new PersistCond1());
        Job job2 = new Job(true, new PersistAct())
                .withExtra(new PersistCond2());
        Job job3 = new Job(true, new PersistContextAct())
                .attachOn(ThreadSpace.MAIN)
                .withExtra(new PersistCond1())
                .withExtra(new PersistCond2());
        trigger.schedule(job1, job2, job3);
        sendBroadcastDelay(DEBUG_OFF_BROADCAST);
        timeoutLatch = new TimeoutLatch(1);

        try {
            timeoutLatch.await(2000);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            File file = new File(getFilesDir(), "job_persist");
            assertTrue(file.exists());
            assertTrue(file.isDirectory());
            assertNotNull(file.listFiles());
            assertEquals(file.listFiles().length, 3);
        }

        //fake device off
        sendBroadcastDelay(DEBUG_ON_BROADCAST);
        SystemClock.sleep(2000);

        timeoutLatch = new TimeoutLatch(1);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }

        timeoutLatch = new TimeoutLatch(2);
        sendBroadcastDelay(COND2);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(actCounter.get(), 3);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test void testCancelJob() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job("ttag0x123", new Act())
                .withExtra(new Cond1());
        trigger.schedule(job);
        trigger.cancel("ttag0x123");
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }
    }

    @Test void testNetworkType() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .networkType(Job.NETWORK_TYPE_NONE)
                .withExtra(new Cond1());
        trigger.schedule(job);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 2);
        } catch (TimeoutException ignore) {
            assertEquals(1, 1);
        }

        timeoutLatch = new TimeoutLatch(1);
        Job job1 = new Job(new Act())
                .networkType(Job.NETWORK_TYPE_ANY)
                .withExtra(new Cond1());
        trigger.schedule(job1);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test void testChargingStatus() {
        timeoutLatch = new TimeoutLatch(1);
        Job job = new Job(new Act())
                .needCharging(true)
                .withExtra(new Cond1());
        trigger.schedule(job);
        sendBroadcastDelay(COND1);
        try {
            timeoutLatch.await(AWAIT_TIME);
            assertEquals(1, 1);
        } catch (TimeoutException ignore) {
            assertEquals(1, 2);
        }
    }

    @Test void testEnsureJobRight() {
        Job job = new Job(new Act())
                .repeat()
                .needCharging(true);
        try {
            trigger.schedule(job);
            assertEquals(1, 2);
        } catch (IllegalArgumentException ignore) {
            assertEquals(1, 1);
        }

        Job job2 = new Job(new Act())
                .needCharging(true)
                .repeat(60 * 1000);
        try {
            trigger.schedule(job2);
            assertEquals(1, 2);
        } catch (IllegalArgumentException ignore) {
            assertEquals(1, 1);
        }

        try {
            Job[] jobs = null;
            trigger.schedule(jobs);
            assertEquals(1, 2);
        } catch (NullPointerException ignore) {
            assertEquals(1, 1);
        }
    }

    private boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    public static class PersistAct extends Action {

        @Override
        protected void act() {
            timeoutLatch.countDown();
            actCounter.incrementAndGet();
        }
    }

    public static class PersistContextAct extends ContextAction {

        @Override
        protected void act(Context context) {
            assertNotNull(context);
            timeoutLatch.countDown();
            actCounter.incrementAndGet();
        }
    }

    public static class PersistCond1 extends Condition {
        @Override
        public String[] getAction() {
            return new String[]{COND1};
        }
    }

    public static class PersistCond2 extends Condition {
        @Override
        public String[] getAction() {
            return new String[]{COND2};
        }
    }

    public static class PersistCond3 extends Condition {
        @Override
        public String[] getAction() {
            return new String[]{COND3};
        }
    }

    abstract class TestAct extends Action {
        @Override
        protected void act() {
            run();
            timeoutLatch.countDown();
            actCounter.incrementAndGet();
        }

        abstract void run();
    }

    class LockAct extends TestAct {

        @Override
        void run() {
            lock.acquire();
            SystemClock.sleep(10);
            lock.release();
        }
    }

    public class UnderAct extends TestAct {

        @Override
        void run() {
            Log.w(TAG, "Under " + isMainThread() + "");
            assertFalse(isMainThread());
        }
    }

    public class MainAct extends TestAct {

        @Override
        void run() {
            Log.w(TAG, "Main " + isMainThread() + "");
            assertTrue(isMainThread());
        }
    }

    public class ActDummy extends Action {

        @Override
        protected void act() {
            //no-op
        }
    }

    class Act extends Action {

        @Override
        protected void act() {
            Log.d(TAG, "Act act");
            timeoutLatch.countDown();
            actCounter.incrementAndGet();
        }
    }

    public class Cond1 extends Condition {

        @Override
        public String[] getAction() {
            return new String[]{COND1};
        }
    }

    class Cond2 extends Condition {

        @Override
        public String[] getAction() {
            return new String[]{COND2};
        }
    }

    public class Cond3 extends Condition {

        @Override
        public String[] getAction() {
            return new String[]{COND3};
        }
    }

}
