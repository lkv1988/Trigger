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

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global device status
 */
final class DeviceStatus {
    // Constraints.
    static final AtomicBoolean chargingConstraintSatisfied = new AtomicBoolean();
    static final AtomicBoolean idleConstraintSatisfied = new AtomicBoolean();
    static final AtomicBoolean unmeteredConstraintSatisfied = new AtomicBoolean();
    static final AtomicBoolean connectivityConstraintSatisfied = new AtomicBoolean();
    private static final Object lock = new Object();
    private static DeviceStatus sInstance;
    public Class<?>[] CONTROLLERS = new Class[]{
            NetworkStatusController.class,
            ChargingStatusController.class,
            IdleStatusController.class
    };
    private List<StatusController> controllers;

    private DeviceStatus(Context context) {
        controllers = new ArrayList<>();
        for (Class<?> controller : CONTROLLERS) {
            try {
                controllers.add((StatusController) controller.newInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (StatusController c : controllers) {
            c.onCreate(context);
        }
    }

    static boolean networkTypeSatisfied(int type) {
        switch (type) {
            case Job.NETWORK_TYPE_NONE:
                return !connectivityConstraintSatisfied.get();
            case Job.NETWORK_TYPE_ANY:
                return connectivityConstraintSatisfied.get();
            case Job.NETWORK_TYPE_UNMETERED:
                return connectivityConstraintSatisfied.get() && unmeteredConstraintSatisfied.get();
            default:
                return false;
        }
    }

    static DeviceStatus get(Context context) {
        synchronized (lock) {
            if (sInstance == null) {
                sInstance = new DeviceStatus(context);
            }
            return sInstance;
        }
    }

    void onDestroy() {
        for (StatusController c : controllers) {
            c.onDestroy();
        }
    }

}
