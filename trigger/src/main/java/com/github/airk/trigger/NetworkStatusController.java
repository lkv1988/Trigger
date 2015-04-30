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
