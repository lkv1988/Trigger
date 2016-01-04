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

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Trigger. A window let you schedule, cancel jobs.
 */
public class Trigger {
    private static final String TAG = "Trigger";
    //just for test
    static final String DEBUG_DEVICE_ON_B = "trigger.testcase.deviceon";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static Trigger sInstance;
    private TriggerLoop.TriggerBinder triggerBinder;
    private Context appContext;
    private HashMap<String, Job> pendingList;
    private boolean stopAndResetPending = false;
    private Connector connector;

    private Trigger(Context context) {
        pendingList = new HashMap<>();
        connector = new Connector();
        appContext = context.getApplicationContext();
        appContext.bindService(TriggerLoop.newIntent(appContext), connector, Context.BIND_AUTO_CREATE);
    }

    public static Trigger getInstance(Context context) {
        if (sInstance == null) {
            synchronized (Trigger.class) {
                if (sInstance == null) {
                    sInstance = new Trigger(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Schedule a job
     *
     * @param job {@link Job} with action and maybe one or more extra conditions.
     * @throw {@link IllegalArgumentException} while this Job is not complete.
     */
    public void schedule(Job job) {
        ensureJobRight(job);
        if (triggerBinder == null) {
            pendingList.put(job.jobInfo.identity, job);
        } else {
            triggerBinder.schedule(job);
        }
    }

    private void ensureJobRight(Job job) {
        if (job == null) {
            throw new NullPointerException("Null Job can not accept!");
        }
        if (job.exConds.isEmpty() && !job.jobInfo.needCharging && job.jobInfo.networkType == Job.NETWORK_TYPE_INVALID
                && !job.jobInfo.needDeviceIdle) {
            throw new IllegalArgumentException("Please check your Job, it can not be triggered at all without any conditions.");
        }
        if (job.exConds.isEmpty() && job.jobInfo.repeat && job.jobInfo.delay < 60 * 60 * 1000/*1 hour*/) {
            throw new IllegalArgumentException("Your job may be triggered too often, please keep the delay above 1 hour.");
        }
    }

    /**
     * Schedule multi job version
     *
     * @param jobs {@link Job} with action and maybe one or more extra conditions
     * @throw {@link IllegalArgumentException} while this Job is not complete.
     */
    public void schedule(Job... jobs) {
        if (jobs == null) {
            throw new NullPointerException("Null objects can not accept!");
        }
        for (Job j : jobs) {
            schedule(j);
        }
    }

    /**
     * Cancel a Job with given tag, if the job is in the pending list, it can be cancelled.
     * If the job's action is in progress, this can not help you.
     *
     * @param tag Given tag
     */
    public void cancel(String tag) {
        if (triggerBinder == null && pendingList.containsKey(tag)) {
            pendingList.remove(tag);
        } else {
            triggerBinder.cancel(tag);
        }
    }

    /**
     * Don't accept any Job from then. Not effect the jobs already in pending list.
     */
    public void closeDoor() {
        triggerBinder = null;
        appContext.unbindService(connector);
    }

    /**
     * Reset all things, include cancel jobs in pending list and clear all jobs status,
     * after this, Trigger can accept new job all the same.
     */
    public void stopAndReset() {
        if (triggerBinder != null) {
            triggerBinder.stopAndReset();
        } else {
            stopAndResetPending = true;
        }
    }

    class Connector implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            triggerBinder = (TriggerLoop.TriggerBinder) service;
            if (stopAndResetPending) {
                triggerBinder.stopAndReset();
                stopAndResetPending = false;
            }
            if (pendingList != null) {
                for (Map.Entry<String, Job> entry : pendingList.entrySet()) {
                    Job job = entry.getValue();
                    triggerBinder.schedule(job);
                }
                pendingList.clear();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }
}
