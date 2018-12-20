package io.smallrye.metrics.exporters;

import org.eclipse.microprofile.metrics.Meter;

/**
 * Provides a second (useless) Meter implementation
 */
public class SomeMeter implements Meter {
    @Override
    public void mark() {

    }

    @Override
    public void mark(long l) {

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
}
