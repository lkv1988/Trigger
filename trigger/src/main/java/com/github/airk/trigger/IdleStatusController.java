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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Idle status monitor
 */
final class IdleStatusController implements StatusController {
    static final String TAG = "IdleStatusController";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.VERBOSE);
    static final String ACTION_TRIGGER_IDLE = "com.github.airk.trigger.controller.idlestatus";

    // Policy: we decide that we're "idle" if the device has been unused /
    // screen off or dreaming for at least this long
    private static final long INACTIVITY_IDLE_THRESHOLD = 71 * 60 * 1000; // millis; 71 min
    private static final long IDLE_WINDOW_SLOP = 5 * 60 * 1000; // 5 minute window, to be nice

    private Context context;
    private IdlenessTracker tracker;

    @Override
    public void onCreate(Context context) {
        this.context = context;
        tracker = new IdlenessTracker();
        tracker.startTracking();
    }

    @Override
    public void parseStatus(Intent data) {
        //no-op
    }

    @Override
    public void onDestroy() {
        tracker.stopTracking();
        tracker = null;
        context = null;
    }

    private void reportNewIdleState(boolean idle) {
        DeviceStatus.idleConstraintSatisfied.set(idle);
        context.startService(TriggerLoop.deviceStatusChanged(context, Job.IDLE_DEVICE_KEY));
    }

    class IdlenessTracker extends BroadcastReceiver {
        boolean mIdle;
        private AlarmManager mAlarm;
        private PendingIntent mIdleTriggerIntent;

        public IdlenessTracker() {
            mAlarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            Intent intent = new Intent(ACTION_TRIGGER_IDLE)
                    .setPackage("android")
                    .setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            mIdleTriggerIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

            // At boot we presume that the user has just "interacted" with the
            // device in some meaningful way.
            mIdle = false;
        }

        public boolean isIdle() {
            return mIdle;
        }

        public void startTracking() {
            IntentFilter filter = new IntentFilter();

            // Screen state
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);

            // Dreaming state
            if (Build.VERSION.SDK_INT >= 17) {
                filter.addAction(Intent.ACTION_DREAMING_STARTED);
                filter.addAction(Intent.ACTION_DREAMING_STOPPED);
            }

            // Debugging/instrumentation
            filter.addAction(ACTION_TRIGGER_IDLE);

            context.registerReceiver(this, filter);
        }

        public void stopTracking() {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                case Intent.ACTION_DREAMING_STOPPED:
                    // possible transition to not-idle
                    if (mIdle) {
                        if (DEBUG) {
                            Log.v(TAG, "exiting idle : " + action);
                        }
                        mAlarm.cancel(mIdleTriggerIntent);
                        mIdle = false;
                        reportNewIdleState(false);
                    }
                    break;
                case Intent.ACTION_SCREEN_OFF:
                case Intent.ACTION_DREAMING_STARTED:
                    // when the screen goes off or dreaming starts, we schedule the
                    // alarm that will tell us when we have decided the device is
                    // truly idle.
                    final long nowElapsed = SystemClock.elapsedRealtime();
                    final long when = nowElapsed + INACTIVITY_IDLE_THRESHOLD;
                    if (DEBUG) {
                        Log.v(TAG, "Scheduling idle : " + action + " now:" + nowElapsed + " when="
                                + when);
                    }
                    if (Build.VERSION.SDK_INT >= 19) {
                        mAlarm.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                when, IDLE_WINDOW_SLOP, mIdleTriggerIntent);
                    } else {
                        mAlarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                when, mIdleTriggerIntent);
                    }
                    break;
                case ACTION_TRIGGER_IDLE:
                    // idle time starts now
                    if (!mIdle) {
                        if (DEBUG) {
                            Log.v(TAG, "Idle trigger fired @ " + SystemClock.elapsedRealtime());
                        }
                        mIdle = true;
                        reportNewIdleState(true);
                    }
                    break;
            }
        }
    }
}
