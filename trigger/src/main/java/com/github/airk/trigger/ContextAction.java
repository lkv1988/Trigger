package com.github.airk.trigger;

import android.content.Context;

/**
 * Compare to {@link Action}, ContextAction bring a context here. The context come from {@link TriggerLoop}
 */
public abstract class ContextAction implements Act {
    public ContextAction() {
    }

    protected abstract void act(Context context);
}
