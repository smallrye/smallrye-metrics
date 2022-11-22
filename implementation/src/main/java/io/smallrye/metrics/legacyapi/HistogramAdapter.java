package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

class HistogramAdapter implements Histogram, MeterHolder {

    private final static int PRECISION;

    /*
     * Increasing the percentile precision for histograms will consume more memory.
     * This setting is "3" by default, and provided to adjust the precision to
     * your needs.
     */
    static {
        final Config config = ConfigProvider.getConfig();
        PRECISION = config.getOptionalValue("mp.metrics.smallrye.histogram.precision", Integer.class).orElse(3);
    }

    DistributionSummary globalCompositeSummary;

    public HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope) {

        if (globalCompositeSummary == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            globalCompositeSummary = DistributionSummary.builder(metricInfo.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(PRECISION)
                    .register(Metrics.globalRegistry);
        }
        return this;
    }

    @Override
    public void update(int i) {
        globalCompositeSummary.record(i);
    }

    @Override
    public void update(long l) {
        globalCompositeSummary.record(l);
    }

    @Override
    public long getCount() {
        return globalCompositeSummary.count();
    }

    @Override
    public long getSum() {
        return (long) globalCompositeSummary.takeSnapshot().total();
    }

    @Override
    public Snapshot getSnapshot() {
        return new SnapshotAdapter(globalCompositeSummary.takeSnapshot());
    }

    @Override
    public Meter getMeter() {
        return globalCompositeSummary;
    }
}
