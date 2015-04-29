package com.github.airk.trigger;

import android.content.Context;
import android.content.Intent;

/**
 * Users should use this to make own conditions for job by extending this class.
 * {@link #getAction()} need user to return the broadcast's actions，it can be one or more,
 * {@link TriggerLoop} will register receivers for each of them, and then
 * {@link #satisfy(Context, Intent)} give user a change by handing the data from {@link #onReceive(Context, Intent)}
 * here and let user to judge whether the data can satisfied your condition, then it will be
 * recorded in the Job's status.
 */
public abstract class Condition implements Receiver {
    private String identify = null;

    @Override
    public String getIdentify() {
        if (identify == null) {
            StringBuilder sb = new StringBuilder();
            for (String act : getAction()) {
                sb.append(act).append("|");
            }
            sb.deleteCharAt(sb.length() - 1);
            identify = sb.toString();
        }
        return identify;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConditionDesc condition = new ConditionDesc(getIdentify());
        condition.setSatisfy(satisfy(context, intent));
        Intent data = TriggerLoop.newIntent(context, condition);
        context.startService(data);
    }

    public abstract String[] getAction();

    protected boolean satisfy(Context context, Intent intent) {
        return true;
    }
}
