package io.smallrye.metrics.legacyapi;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.MetricRegistries;

class HistogramAdapter implements Histogram, MeterHolder {
    DistributionSummary summary;

    HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry) {
        MetricRegistries.MP_APP_METER_REG_ACCESS.set(true);
        if (summary == null || metadata.cleanDirtyMetadata()) {
            summary = DistributionSummary.builder(metricInfo.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(metricInfo.tags())
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }
        MetricRegistries.MP_APP_METER_REG_ACCESS.set(false);
        return this;
    }

    @Override
    public void update(int i) {
        summary.record(i);
    }

    @Override
    public void update(long l) {
        summary.record(l);
    }

    @Override
    public long getCount() {
        return summary.count();
    }

    @Override
    public long getSum() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
        //If we keep this call.
        //summary.takeSnapshot().total();
    }

    /** Not supported. */
    @Override
    public Snapshot getSnapshot() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
    }

    @Override
    public Meter getMeter() {
        return summary;
    }

    @Override
    public MetricType getType() {
        return MetricType.HISTOGRAM;
    }
}
