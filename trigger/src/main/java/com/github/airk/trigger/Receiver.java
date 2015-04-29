package com.github.airk.trigger;

import android.content.Context;
import android.content.Intent;

/**
 * Created by kevin on 15/4/22.
 */
interface Receiver {
    void onReceive(Context context, Intent intent);
    String getIdentify();
}
