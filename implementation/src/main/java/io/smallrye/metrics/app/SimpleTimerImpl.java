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

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.microprofile.metrics.SimpleTimer;

public class SimpleTimerImpl implements SimpleTimer {

    private final Clock clock;

    // total number of captured measurements
    private final LongAdder count;

    // total elapsed time across all measurements
    private final LongAdder elapsedTime;

    // maximum duration achieved in previous minute
    private final AtomicReference<Duration> max_previousMinute;
    // minimum duration achieved in previous minute
    private final AtomicReference<Duration> min_previousMinute;

    // maximum duration achieved in this minute
    private final AtomicReference<Duration> max_thisMinute;
    // minimum duration achieved in this minute
    private final AtomicReference<Duration> min_thisMinute;

    // current timestamp rounded down to the last whole minute
    private final AtomicLong thisMinute;

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
        this.thisMinute = new AtomicLong(getCurrentMinuteFromSystem());
        this.max_previousMinute = new AtomicReference<>(null);
        this.min_previousMinute = new AtomicReference<>(null);
        this.max_thisMinute = new AtomicReference<>(null);
        this.min_thisMinute = new AtomicReference<>(null);
    }

    /**
     * Adds a recorded duration.
     *
     * @param duration the length of the duration
     */
    public void update(Duration duration) {
        if (duration.compareTo(Duration.ZERO) > 0) {
            maybeStartNewMinute();
            synchronized (this) {
                count.increment();
                elapsedTime.add(duration.toNanos());
                Duration currentMax = max_thisMinute.get();
                if (currentMax == null || (duration.compareTo(currentMax) > 0)) {
                    max_thisMinute.set(duration);
                }
                Duration currentMin = min_thisMinute.get();
                if (currentMin == null || (duration.compareTo(currentMin) < 0)) {
                    min_thisMinute.set(duration);
                }
            }
        }
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
            update(Duration.ofNanos(clock.getTick() - startTime));
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
            update(Duration.ofNanos(clock.getTick() - startTime));
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
    public Duration getElapsedTime() {
        return Duration.ofNanos(elapsedTime.sum());
    }

    @Override
    public long getCount() {
        return count.sum();
    }

    @Override
    public Duration getMaxTimeDuration() {
        maybeStartNewMinute();
        return max_previousMinute.get();
    }

    @Override
    public Duration getMinTimeDuration() {
        maybeStartNewMinute();
        return min_previousMinute.get();
    }

    private void maybeStartNewMinute() {
        long newMinute = getCurrentMinuteFromSystem();
        if (newMinute > thisMinute.get()) {
            synchronized (this) {
                if (newMinute > thisMinute.get()) {
                    thisMinute.set(newMinute);
                    max_previousMinute.set(max_thisMinute.get());
                    min_previousMinute.set(min_thisMinute.get());
                    max_thisMinute.set(null);
                    min_thisMinute.set(null);
                }
            }
        }
    }

    // Get the current system time in minutes, truncating. This number will increase by 1 every complete minute.
    private long getCurrentMinuteFromSystem() {
        return System.currentTimeMillis() / 60000;
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
            timer.update(Duration.ofNanos(elapsed));
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
