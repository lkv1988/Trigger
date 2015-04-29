package com.github.airk.trigger;

import android.support.annotation.IntDef;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The Job you want to schedule
 */
public final class Job {
    public static final int NETWORK_TYPE_NONE = 0;
    public static final int NETWORK_TYPE_ANY = 1;
    public static final int NETWORK_TYPE_UNMETERED = 2;
    static final String NETWORK_TYPE_KEY = "con_networktype";
    static final String CHARGING_KEY = "con_charging";
    static final String IDLE_DEVICE_KEY = "con_idle";
    static final int NETWORK_TYPE_INVALID = -1;
    private static final String TAG = "Job";
    private static final int SECRET_CODE = 0x611;
    HashMap<String, Boolean> condSatisfied = new HashMap<>();
    JobInfo jobInfo = new JobInfo();
    List<Condition> exConds = new ArrayList<>();
    Act action;
    Object deadLineObj = null; //Store deadline Object, list of PendingIntent(after >5min) or Runnable(otherwise)
    boolean canBePersist = true;

    //full-version
    public Job(boolean persisAfterReboot, String tag, Action act) {
        jobInfo.persistAfterReboot = persisAfterReboot;
        setAction(act);
        jobInfo.tag = tag;
        jobInfo.identity = generateIdentity();
    }

    public Job(boolean persisAfterReboot, Action act) {
        this(persisAfterReboot, TAG, act);
    }

    public Job(String tag, Action act) {
        this(false, tag, act);
    }

    public Job(Action act) {
        this(false, TAG, act);
    }

    //full-version
    public Job(boolean persisAfterReboot, String tag, ContextAction act) {
        jobInfo.persistAfterReboot = persisAfterReboot;
        setAction(act);
        jobInfo.tag = tag;
        jobInfo.identity = generateIdentity();
    }

    public Job(boolean persisAfterReboot, ContextAction act) {
        this(persisAfterReboot, TAG, act);
    }

    public Job(String tag, ContextAction act) {
        this(false, tag, act);
    }

    public Job(ContextAction act) {
        this(false, TAG, act);
    }

    private Job() {
    }

    static Job createJobFromPersistInfo(JobInfo existInfo) {
        if (existInfo == null)
            return null;
        Job job = new Job();
        job.jobInfo = existInfo;
        try {
            Class<?> clz = Class.forName(job.jobInfo.actionClzName);
            Object act = clz.newInstance();
            if (act instanceof Action) {
                job.action = (Action) act;
            } else if (act instanceof ContextAction) {
                job.action = (ContextAction) act;
            }
            for (String condName : job.jobInfo.conditions) {
                Class<?> condClz = Class.forName(condName);
                Condition cond = (Condition) condClz.newInstance();
                job.exConds.add(cond);
            }
            job.resetConds();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return job;
    }

    String generateIdentity() {
        String actName = (TextUtils.isEmpty(action.getClass().getSimpleName()) ? "Anonymous" : action.getClass().getSimpleName());
        return String.valueOf(SECRET_CODE) + "_" + System.identityHashCode(this) + "_" + actName;
    }

    private void setAction(Act action) {
        this.action = action;
        if (action.getClass().getCanonicalName() == null) {
            canBePersist = false;
            if (jobInfo.persistAfterReboot) {
                throw new IllegalArgumentException("Do not use anonymous class as Action");
            } else {
                Log.w(TAG, "Job with anonymous Action class can not be persist as file, so we will lose it while service restarting.");
            }
        }
        int modifier = action.getClass().getModifiers();
        if ((modifier & Modifier.PUBLIC) == 0 || (modifier & Modifier.STATIC) == 0) {
            canBePersist = false;
            if (jobInfo.persistAfterReboot) {
                throw new IllegalArgumentException("Action must be PUBLIC and STATIC.");
            } else {
                Log.w(TAG, "If you want this Job can be persist, please keep Action class as PUBLIC and STATIC. So we can recover it from service restarting.");
            }
        }
        jobInfo.actionClzName = action.getClass().getName();
    }

    public Job withExtra(Condition condition) {
        if (condition.getClass().getCanonicalName() == null) {
            canBePersist = false;
            if (jobInfo.persistAfterReboot) {
                throw new IllegalArgumentException("Do not use anonymous class as Condition");
            } else {
                Log.w(TAG, "Job with anonymous Condition class can not be persist as file, so we will lose it while service restarting.");
            }
        }
        int modifier = condition.getClass().getModifiers();
        if ((modifier & Modifier.PUBLIC) == 0 || (modifier & Modifier.STATIC) == 0) {
            canBePersist = false;
            if (jobInfo.persistAfterReboot) {
                throw new IllegalArgumentException("Condition must be PUBLIC and STATIC.");
            } else {
                Log.w(TAG, "If you want this Job can be persist, please keep Condition class as PUBLIC and STATIC. So we can recover it from service restarting.");
            }
        }
        jobInfo.conditions.add(condition.getClass().getName());
        exConds.add(condition);
        condSatisfied.put(condition.getIdentify(), false);
        return this;
    }

    void resetConds() {
        condSatisfied.clear();
        for (Condition c : exConds) {
            condSatisfied.put(c.getIdentify(), false);
        }
        if (jobInfo.networkType != NETWORK_TYPE_INVALID) {
            networkTypeInternal(jobInfo.networkType);
        }
        if (jobInfo.needCharging) {
            needCharging(true);
        }
        if (jobInfo.needDeviceIdle) {
            needDeviceIdle(true);
        }
    }

    public Job repeat() {
        jobInfo.repeat = true;
        return this;
    }

    public Job repeat(long delay) {
        if (delay <= 0) {
            throw new IllegalArgumentException("Delay must greater than 0.");
        }
        jobInfo.repeat = true;
        jobInfo.delay = delay;
        return this;
    }

    public Job deadline(long deadlineInMs) {
        if (deadlineInMs <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Deadline time must be greater than now.");
        }
        jobInfo.deadline = deadlineInMs;
        return this;
    }

    public Job attachOn(ThreadSpace space) {
        jobInfo.threadSpace = space;
        return this;
    }

    public Job networkType(@NetworkType int type) {
        return networkTypeInternal(type);
    }

    private Job networkTypeInternal(int type) {
        condSatisfied.put(NETWORK_TYPE_KEY, DeviceStatus.networkTypeSatisfied(type));
        jobInfo.networkType = type;
        return this;
    }

    public Job needCharging(boolean charge) {
        if (charge) {
            condSatisfied.put(CHARGING_KEY, DeviceStatus.chargingConstraintSatisfied.get());
        }
        jobInfo.needCharging = charge;
        return this;
    }

    public Job needDeviceIdle(boolean idle) {
        if (idle) {
            condSatisfied.put(IDLE_DEVICE_KEY, DeviceStatus.idleConstraintSatisfied.get());
        }
        jobInfo.needDeviceIdle = idle;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Job))
            return false;
        Job other = (Job) o;
        if (!this.jobInfo.equals(other.jobInfo)) return false;
        if (this.canBePersist != other.canBePersist) return false;
        return true;
    }

    @IntDef({NETWORK_TYPE_NONE, NETWORK_TYPE_ANY, NETWORK_TYPE_UNMETERED})
    public @interface NetworkType {
    }

    //for persist after reboot
    static class JobInfo {
        @SerializedName("persist")
        boolean persistAfterReboot = false;
        @SerializedName("thread")
        ThreadSpace threadSpace = ThreadSpace.BACKGROUND;
        @SerializedName("network")
        int networkType = NETWORK_TYPE_INVALID;
        @SerializedName("charging")
        boolean needCharging = false;
        @SerializedName("idle")
        boolean needDeviceIdle = false;
        String identity;
        boolean repeat = false;
        long delay = -1L;
        long deadline = -1L;
        long happen = -1L;
        @SerializedName("conds")
        List<String> conditions = new ArrayList<>();
        @SerializedName("actname")
        String actionClzName;
        String tag;

        public static JobInfo readFromFile(File file) throws IOException {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder ret = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                ret.append(line).append("\n");
            }
            String str = ret.toString();
            return new Gson().fromJson(str, JobInfo.class);
        }

        public void writeToFile(File dir) throws IOException {
            File file = new File(dir, identity + ".job");
            //do this if this method is only called because of device shutdown
            happen = -1L;
            String content = new Gson().toJson(this);
            Writer writer = new FileWriter(file);
            writer.write(content);
            writer.flush();
            writer.close();
        }

        public void tryDelete(File dir) {
            File file = new File(dir, identity + ".job");
            if (file.exists() && file.isFile()) {
                file.delete();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof JobInfo))
                return false;
            JobInfo other = (JobInfo) o;
            //drop happen and identify
            if (this.persistAfterReboot != other.persistAfterReboot) return false;
            if (this.threadSpace != other.threadSpace) return false;
            if (this.networkType != other.networkType) return false;
            if (this.needCharging != other.needCharging) return false;
            if (this.needDeviceIdle != other.needDeviceIdle) return false;
            if (this.repeat != other.repeat) return false;
            if (this.delay != other.delay) return false;
            if (this.deadline != other.deadline) return false;
            if (!this.conditions.equals(other.conditions)) return false;
            if (!this.actionClzName.equals(other.actionClzName)) return false;
            if (!this.tag.equals(other.tag)) return false;
            return true;
        }
    }
}
