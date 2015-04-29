package com.github.airk.trigger;

/**
 * Normal action without {@link android.content.Context}, if you want this,
 * you should see {@link ContextAction}
 */
public abstract class Action implements Act {
    public Action() {
    }

    protected abstract void act();
}
