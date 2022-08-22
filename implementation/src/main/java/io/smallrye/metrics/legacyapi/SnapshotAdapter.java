package io.smallrye.metrics.legacyapi;

import java.io.OutputStream;
import java.io.PrintStream;

import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

public class SnapshotAdapter extends Snapshot {

    private HistogramSnapshot histogramSnapshot;

    public SnapshotAdapter(HistogramSnapshot histogramSnapshot) {
        this.histogramSnapshot = histogramSnapshot;
    }

    @Override
    public long size() {
        return histogramSnapshot.count();
    }

    @Override
    public double getMax() {
        return histogramSnapshot.max();
    }

    @Override
    public double getMean() {
        return histogramSnapshot.mean();
    }

    @Override
    public PercentileValue[] percentileValues() {
        ValueAtPercentile[] valueAtPercentiles = histogramSnapshot.percentileValues();
        PercentileValue[] percentileValues = new PercentileValue[valueAtPercentiles.length];

        for (int i = 0; i < valueAtPercentiles.length; i++) {
            percentileValues[i] = new PercentileValue(valueAtPercentiles[i].percentile(), valueAtPercentiles[i].value());
        }

        return percentileValues;
    }

    @Override
    public void dump(OutputStream output) {
        histogramSnapshot.outputSummary(new PrintStream(output), 1);

    }

}
