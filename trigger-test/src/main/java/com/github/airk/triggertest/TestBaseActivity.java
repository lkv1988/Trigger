package com.github.airk.triggertest;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.airk.trigger.Trigger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Stack;


/**
 * Created by kevin on 15/4/23.
 */
public abstract class TestBaseActivity extends AppCompatActivity {

    TextView textView;
    ProgressBar progressBar;
    Trigger trigger;
    Stack<Method> testMethod;
    Method setUp;
    Method tearDown;
    TestRunner runner;
    int total;
    int remain = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        textView = (TextView) findViewById(R.id.text);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        textView.setText("Begin");
        trigger = Trigger.getInstance(this);
        runner = new TestRunner(this);
        runner.start();

        findOutTestMethod();
        runTestMethod();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        runner.quit();
    }

    private void findOutTestMethod() {
        testMethod = new Stack<>();
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getAnnotation(Test.class) != null) {
                testMethod.push(m);
            } else if (m.getAnnotation(SetUp.class) != null) {
                setUp = m;
            } else if (m.getAnnotation(TearDown.class) != null) {
                tearDown = m;
            }
        }
        total = testMethod.size();
        Log.d("TEST", total + " method to be tested...");
    }

    private void runTestMethod() {
        final int MSG = 1;
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (!testMethod.empty()) {
                    runner.runTest(testMethod.pop());
                    sendEmptyMessage(MSG);
                } else {
                    runner.runTest(null);
                }

            }
        };
        handler.sendEmptyMessageDelayed(MSG, 500);
    }

    void makeToast(final CharSequence cs) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TestBaseActivity.this, cs, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static class TestRunner extends HandlerThread {
        final TestBaseActivity owner;
        Handler handler;

        public TestRunner(TestBaseActivity owner) {
            super("TestRunner");
            this.owner = owner;
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            handler = new Handler(getLooper());
        }

        public void runTest(final Method m) {
            if (m == null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        owner.textView.post(new Runnable() {
                            @Override
                            public void run() {
                                owner.textView.setAlpha(0f);
                                owner.textView.setText("DONE");
                                owner.textView.animate().alpha(1f).setDuration(380);
                                owner.progressBar.setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                });
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        owner.makeToast("All test succeed.");
                        owner.finish();
                    }
                }, 500);
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            owner.remain++;
                            owner.textView.post(new Runnable() {
                                @Override
                                public void run() {
                                    owner.textView.setText(m.getName());
                                    float f = (float) owner.remain / owner.total;
                                    owner.progressBar.setProgress((int) (100 * f));
                                }
                            });
                            if (owner.setUp != null) {
                                owner.setUp.invoke(owner, m.getName());
                            }
                            m.invoke(owner);
                            if (owner.tearDown != null) {
                                owner.tearDown.invoke(owner);
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                            owner.makeToast("Test failed: " + m.getName());
                            throw new RuntimeException("Test failed: " + m.getName());
                        }
                    }
                });
            }
        }
    }
}
