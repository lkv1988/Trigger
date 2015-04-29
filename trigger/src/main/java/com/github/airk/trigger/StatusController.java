package com.github.airk.trigger;

import android.content.Context;
import android.content.Intent;

/**
 * Created by kevin on 15/4/27.
 */
interface StatusController {
    void onCreate(Context context);

    void parseStatus(Intent data);

    void onDestroy();
}
