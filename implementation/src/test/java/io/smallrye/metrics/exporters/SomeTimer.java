package io.smallrye.metrics.exporters;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

public class SomeTimer implements Timer {

    private Snapshot snapshot = new SomeSnapshot();

    @Override
    public void update(long duration, TimeUnit unit) {
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
