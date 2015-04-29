package com.trigger.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.github.airk.tinyalfred.TinyAlfred;
import com.github.airk.tinyalfred.annotation.OnClick;
import com.github.airk.trigger.Action;
import com.github.airk.trigger.Condition;
import com.github.airk.trigger.ContextAction;
import com.github.airk.trigger.Job;
import com.github.airk.trigger.ThreadSpace;
import com.github.airk.trigger.Trigger;


public class MainActivity extends AppCompatActivity {
    final String CUSTOM_COND1 = "trigger.sample.custom_1";
    final String CUSTOM_COND2 = "trigger.sample.custom_2";
    final String CUSTOM_COND3 = "trigger.sample.custom_3";
    final String CUSTOM_COND4 = "trigger.sample.custom_4";

    Trigger trigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TinyAlfred.process(this);
        trigger = Trigger.getInstance(this);
        trigger.stopAndReset();
        Job job1 = new Job(new Action() {
            @Override
            protected void act() {
                Toast.makeText(MainActivity.this, "Custom Job 1", Toast.LENGTH_SHORT).show();
            }
        }).attachOn(ThreadSpace.MAIN)
                .repeat()
                .withExtra(new Condition() {
                    @Override
                    public String[] getAction() {
                        return new String[]{CUSTOM_COND1};
                    }
                });
        Job job2 = new Job(new ContextAction() {
            @Override
            protected void act(Context context) {
                Toast.makeText(context, context.toString(), Toast.LENGTH_SHORT).show();
            }
        }).attachOn(ThreadSpace.MAIN)
                .repeat()
                .withExtra(new Condition() {
                    @Override
                    public String[] getAction() {
                        return new String[]{CUSTOM_COND2};
                    }
                });
        Job job3 = new Job(new ContextAction() {
            @Override
            protected void act(Context context) {
                Toast.makeText(context, "Charging with custom...", Toast.LENGTH_SHORT).show();
            }
        }).attachOn(ThreadSpace.MAIN)
                .needCharging(true)
                .repeat()
                .withExtra(new Condition() {
                    @Override
                    public String[] getAction() {
                        return new String[]{CUSTOM_COND3};
                    }
                });

        Job persistAfterRebootJob = new Job(true, new PersistAfterRebootWithChargingAction())
                .attachOn(ThreadSpace.MAIN)
                .repeat(71 * 60 * 1000)
                .needCharging(true);
        trigger.schedule(job1, job2, job3, persistAfterRebootJob);
    }

    @OnClick(R.id.customJob1)
    void triggerJob1() {
        sendBroadcast(new Intent(CUSTOM_COND1));
    }

    @OnClick(R.id.customJob2)
    void triggerJob2() {
        sendBroadcast(new Intent(CUSTOM_COND2));
    }

    @OnClick(R.id.chargingWithCustom)
    void triggerJob3() {
        sendBroadcast(new Intent(CUSTOM_COND3));
    }

    public static class PersistAfterRebootWithChargingAction extends ContextAction {

        @Override
        protected void act(Context context) {
            Toast.makeText(context, "Charging...(from persist Job after reboot)", Toast.LENGTH_SHORT).show();
        }
    }

}
