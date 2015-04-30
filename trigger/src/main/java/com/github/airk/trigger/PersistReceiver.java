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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * If you want persist your jobs after device rebooting,
 * enable this by declare this class in you AndroidManifest.xml
 * with two action:
 * <p/>
 * android.intent.action.BOOT_COMPLETED
 */
public class PersistReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case Trigger.DEBUG_DEVICE_ON_B:
                if (!Trigger.DEBUG)
                    break;
            case Intent.ACTION_BOOT_COMPLETED:
                context.startService(TriggerLoop.deviceOn(context));
                break;
            default:
                break;
        }
    }
}
