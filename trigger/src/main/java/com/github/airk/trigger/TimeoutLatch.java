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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A timeout latch, use by Trigger test only.
 */
public final class TimeoutLatch {
    AtomicInteger counter;

    public TimeoutLatch(int count) {
        counter = new AtomicInteger(count);
    }

    public void await(long millisecs) throws TimeoutException {
        Timer timer = new Timer();
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] timeout = {false};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int count = counter.get();
                latch.countDown();
                timeout[0] = count != 0;
            }
        }, millisecs);
        try {
            latch.await();
            if (timeout[0]) {
                throw new TimeoutException("Timeout");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void countDown() {
        counter.decrementAndGet();
    }
}
