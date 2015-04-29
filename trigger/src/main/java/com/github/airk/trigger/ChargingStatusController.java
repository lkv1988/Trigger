package com.github.airk.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Listening device charge status changing
 */
final class ChargingStatusController implements StatusController {
    Context context;
    ChargingReceiver receiver;

    @Override
    public void onCreate(Context context) {
        this.context = context;
        parseStatus(null);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        receiver = new ChargingReceiver();
        context.registerReceiver(receiver, filter);
    }

    @Override
    public void parseStatus(Intent data) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
//            int chargePlug = data.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
//            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
//            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
            DeviceStatus.chargingConstraintSatisfied.set(isCharging);
        }
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
        receiver = null;
        context = null;
    }

    private class ChargingReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            parseStatus(null);
            context.startService(TriggerLoop.deviceStatusChanged(context, Job.CHARGING_KEY));
        }
    }
}
