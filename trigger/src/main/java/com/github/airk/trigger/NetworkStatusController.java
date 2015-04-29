package com.github.airk.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * Listening device network status changing
 */
final class NetworkStatusController implements StatusController {
    ConnectivityManager cm;
    Context context;
    ConnectionReceiver receiver;

    @Override
    public void onCreate(Context context) {
        this.context = context;
        cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //update related status ASAP
        parseStatus(null);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new ConnectionReceiver();
        context.registerReceiver(receiver, filter);
    }

    @Override
    public void parseStatus(Intent data) {
        NetworkInfo network = cm.getActiveNetworkInfo();
        boolean connected = false;
        if (network != null) {
            connected = network.isConnected();
        }
        DeviceStatus.connectivityConstraintSatisfied.set(connected);
        DeviceStatus.unmeteredConstraintSatisfied.set(connected && !isNetworkMetered());
    }

    boolean isNetworkMetered() {
        if (cm.getActiveNetworkInfo() == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 16) {
            return cm.isActiveNetworkMetered();
        } else {
            return cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_MOBILE;
        }
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
        cm = null;
        context = null;
    }

    private class ConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            parseStatus(intent);
            context.startService(TriggerLoop.deviceStatusChanged(context, Job.NETWORK_TYPE_KEY));
        }
    }
}
