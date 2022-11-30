package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

class HistogramAdapter implements Histogram, MeterHolder {

    private static final String CLASS_NAME = HistogramAdapter.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private final static int PRECISION;

    /*
     * Increasing the percentile precision for histograms will consume more memory.
     * This setting is "3" by default, and provided to adjust the precision to
     * your needs.
     */
    static {
        PRECISION = ConfigProvider.getConfig().getOptionalValue("mp.metrics.smallrye.histogram.precision", Integer.class)
                .orElse(3);
        LOGGER.logp(Level.FINE, CLASS_NAME, null,
                "Resolved MicroProfile Config value for mp.metrics.smallrye.histogram.precision as \"{0}\"", PRECISION);
    }

    DistributionSummary globalCompositeSummary;

    public HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope,
            Tag... globalTags) {

        if (globalCompositeSummary == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }

            if (globalTags != null) {
                for (Tag t : globalTags) {
                    tagsSet.add(t);
                }
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
