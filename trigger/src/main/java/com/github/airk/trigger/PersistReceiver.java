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
