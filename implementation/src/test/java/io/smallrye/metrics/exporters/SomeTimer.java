/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.smallrye.metrics.exporters;

import java.time.Duration;
import java.util.concurrent.Callable;

import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

public class SomeTimer implements Timer {

    private Snapshot snapshot = new SomeSnapshot();

    @Override
    public void update(Duration duration) {
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return null;
    }

    @Override
    public void time(Runnable event) {
    }

    @Override
    public Context time() {
        return null;
    }

    @Override
    public Duration getElapsedTime() {
        return Duration.ZERO;
    }

    @Override
    public long getCount() {
        return 0;
    }

    @Override
    public double getFifteenMinuteRate() {
        return 0;
    }

    @Override
    public double getFiveMinuteRate() {
        return 0;
    }

    @Override
    public double getMeanRate() {
        return 0;
    }

    @Override
    public double getOneMinuteRate() {
        return 0;
    }

    @Override
    public Snapshot getSnapshot() {
        return snapshot;
    }
}
