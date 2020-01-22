/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * *******************************************************************************
 * Copyright 2010-2013 Coda Hale and Yammer, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics.app;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.microprofile.metrics.SimpleTimer;

public class SimpleTimerImpl implements SimpleTimer {

    private final Clock clock;

    // total number of captured measurements
    private final LongAdder count;

    // total elapsed time across all measurements
    private final LongAdder elapsedTime;

    /**
     * Creates a new {@link SimpleTimerImpl} using the default {@link Clock}.
     */
    public SimpleTimerImpl() {
        this(Clock.defaultClock());
    }

    /**
     * Creates a new {@link SimpleTimerImpl} that uses the given {@link Clock}.
     *
     * @param clock the {@link Clock} implementation the timer should use
     */
    public SimpleTimerImpl(Clock clock) {
        this.clock = clock;
        this.count = new LongAdder();
        this.elapsedTime = new LongAdder();
    }

    /**
     * Adds a recorded duration.
     *
     * @param duration the length of the duration
     * @param unit the scale unit of {@code duration}
     */
    public void update(long duration, TimeUnit unit) {
        update(unit.toNanos(duration));
    }

    /**
     * Times and records the duration of event.
     *
     * @param event a {@link Callable} whose {@link Callable#call()} method implements a process
     *        whose duration should be timed
     * @param <T> the type of the value returned by {@code event}
     * @return the value returned by {@code event}
     * @throws Exception if {@code event} throws an {@link Exception}
     */
    public <T> T time(Callable<T> event) throws Exception {
        final long startTime = clock.getTick();
        try {
            return event.call();
        } finally {
            update(clock.getTick() - startTime);
        }
    }

    /**
     * Times and records the duration of event.
     *
     * @param event a {@link Runnable} whose {@link Runnable#run()} method implements a process
     *        whose duration should be timed
     */
    public void time(Runnable event) {
        final long startTime = clock.getTick();
        try {
            event.run();
        } finally {
            update(clock.getTick() - startTime);
        }
    }

    /**
     * Returns a new {@link Context}.
     *
     * @return a new {@link Context}
     * @see Context
     */
    public Context time() {
        return new SimpleTimerImpl.Context(this, clock);
    }

    @Override
    public long getElapsedTime() {
        return elapsedTime.sum();
    }

    @Override
    public long getCount() {
        return count.sum();
    }

    private void update(long duration) {
        if (duration >= 0) {
            count.increment();
            elapsedTime.add(duration);
        }
    }

    /**
     * A timing context.
     *
     * @see SimpleTimerImpl#time()
     */
    public static class Context implements SimpleTimer.Context {
        private final SimpleTimerImpl timer;
        private final Clock clock;
        private final long startTime;

        private Context(SimpleTimerImpl timer, Clock clock) {
            this.timer = timer;
            this.clock = clock;
            this.startTime = clock.getTick();
        }

        /**
         * Updates the timer with the difference between current and start time. Call to this method will
         * not reset the start time. Multiple calls result in multiple updates.
         *
         * @return the elapsed time in nanoseconds
         */
        public long stop() {
            final long elapsed = clock.getTick() - startTime;
            timer.update(elapsed, TimeUnit.NANOSECONDS);
            return elapsed;
        }

        /**
         * Equivalent to calling {@link #stop()}.
         */
        @Override
        public void close() {
            stop();
        }
    }

}
