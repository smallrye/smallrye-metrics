package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.DistributionSummary.Builder;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.setup.config.DefaultBucketConfiguration;
import io.smallrye.metrics.setup.config.HistogramBucketConfiguration;
import io.smallrye.metrics.setup.config.HistogramBucketMaxConfiguration;
import io.smallrye.metrics.setup.config.HistogramBucketMinConfiguration;
import io.smallrye.metrics.setup.config.MetricPercentileConfiguration;
import io.smallrye.metrics.setup.config.MetricsConfigurationManager;

class HistogramAdapter implements Histogram, MeterHolder {

    private static final String CLASS_NAME = HistogramAdapter.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private final static int PRECISION;

    /*
     * Increasing the percentile precision for histograms will consume more memory. This setting is "3"
     * by default, and provided to adjust the precision to your needs.
     */
    static {
        PRECISION = ConfigProvider.getConfig()
                .getOptionalValue("mp.metrics.smallrye.histogram.precision", Integer.class).orElse(3);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, CLASS_NAME, null,
                    "Resolved MicroProfile Config value for mp.metrics.smallrye.histogram.precision as \"{0}\"", PRECISION);
        }
    }

    DistributionSummary globalCompositeSummary;

    public HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, String scope,
            Tag... globalTags) {

        if (globalCompositeSummary == null || metadata.cleanDirtyMetadata()) {

            MetricPercentileConfiguration percentilesConfig = MetricsConfigurationManager.getInstance()
                    .getPercentilesConfiguration(metadata.getName());

            HistogramBucketConfiguration bucketsConfig = MetricsConfigurationManager.getInstance()
                    .getHistogramBucketConfiguration(metadata.getName());

            DefaultBucketConfiguration defaultBucketConfig = MetricsConfigurationManager.getInstance()
                    .getDefaultBucketConfiguration(metadata.getName());

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

            Builder builder = DistributionSummary.builder(metricInfo.name()).description(metadata.getDescription())
                    .baseUnit(metadata.getUnit()).tags(tagsSet).percentilePrecision(PRECISION);

            if (percentilesConfig != null && percentilesConfig.getValues() != null
                    && percentilesConfig.getValues().length > 0) {
                double[] vals = Stream.of(percentilesConfig.getValues()).mapToDouble(Double::doubleValue).toArray();
                builder = builder.publishPercentiles(vals);
            } else if (percentilesConfig != null && percentilesConfig.getValues() == null
                    && percentilesConfig.isDisabled()) {
                //do nothing - percentiles were disabled
            } else {
                builder = builder.publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
            }

            if (bucketsConfig != null && bucketsConfig.getValues().length > 0) {
                double[] vals = Stream.of(bucketsConfig.getValues()).mapToDouble(Double::doubleValue).toArray();
                builder = builder.serviceLevelObjectives(vals);
            }

            if (defaultBucketConfig != null && defaultBucketConfig.isEnabled()) {

                builder = builder.publishPercentileHistogram(defaultBucketConfig.isEnabled());

                HistogramBucketMaxConfiguration defaultBucketMaxConfig = MetricsConfigurationManager.getInstance()
                        .getDefaultHistogramMaxBucketConfiguration(metadata.getName());

                if (defaultBucketMaxConfig != null && defaultBucketMaxConfig.getValue() != null
                        && defaultBucketMaxConfig.getValue() != Double.NaN) {
                    builder = builder.maximumExpectedValue(defaultBucketMaxConfig.getValue());
                }

                HistogramBucketMinConfiguration defaultBucketMinConfig = MetricsConfigurationManager.getInstance()
                        .getDefaultHistogramMinBucketConfiguration(metadata.getName());

                if (defaultBucketMinConfig != null && defaultBucketMinConfig.getValue() != null
                        && defaultBucketMinConfig.getValue() != Double.NaN) {
                    builder = builder.minimumExpectedValue(defaultBucketMinConfig.getValue());
                }
            }

            globalCompositeSummary = builder.register(Metrics.globalRegistry);

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
