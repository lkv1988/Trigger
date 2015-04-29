package com.github.airk.trigger;

/**
 * Thread space define
 */
public enum ThreadSpace {
    /**
     * Your action will be execute in a ThreadPoll, so you don't
     * need to worry about it will effect the UI thread.
     */
    BACKGROUND,
    /**
     * Your action will be execute in the UI thread, watch out.
     */
    MAIN
}
