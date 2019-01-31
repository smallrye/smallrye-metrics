package io.smallrye.metrics.exporters;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

public class SomeHistogram implements Histogram {

    private Snapshot snapshot = new SomeSnapshot();

    @Override
    public void update(int value) {
    }

    @Override
    public void update(long value) {
    }

    @Override
    public long getCount() {
        return 0;
    }

    @Override
    public Snapshot getSnapshot() {
        return snapshot ;
    }
}
