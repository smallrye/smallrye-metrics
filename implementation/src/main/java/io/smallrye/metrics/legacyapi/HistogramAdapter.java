package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

class HistogramAdapter implements Histogram, MeterHolder {
    DistributionSummary summary;

    /*
     * Have to hold on to meta data for get* methods. Due to multiple Prometheus
     * meter registries being registered to the global composite meter registry with
     * deny filters used, this can lead to a problem when the composite meter is
     * retrieving a value of the meter. It will chose the "first" meter registry
     * associated to the composite meter. This meter registry may have returned a
     * Noop meter (due it being denied). As a result, querying this composite meter
     * for a value can return a 0.
     * 
     * See SharedMetricRegistries.java for more information.
     * 
     * We do not save the prometheus meter regsitry's meter by itself as the
     * composite meter registry is needed for the getMeter() call which is used by
     * remove calls that interact with the global meter registry. Or it could be
     * that the composite meter is needed still to record data.
     * 
     * We do not save the prometheus meter registry's meter and the composite meter
     * registry together as we do not anticipate high usage of explicit get* calls
     * from the Metric API for the Metric so we save memory in favour of the process
     * overhead.
     */
    MeterRegistry registry;
    MetricDescriptor descriptor;
    Set<Tag> tagsSet = new HashSet<Tag>();

    HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);

        threadLocal.set(true);
        if (summary == null || metadata.cleanDirtyMetadata()) {

            this.registry = registry;
            this.descriptor = metricInfo;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            summary = DistributionSummary.builder(metricInfo.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }
        threadLocal.set(false);
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
    /*
     * Due to registries that deny regsitration returning no-op and the chance of the composite meter
     * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
     */
    public long getCount() {
        DistributionSummary promDistSum = registry.find(descriptor.name()).tags(tagsSet).summary();
        if (promDistSum != null) {
            return (long) promDistSum.count();
        }
        return summary.count();
    }

    @Override
    /*
     * Due to registries that deny regsitration returning no-op and the chance of the composite meter
     * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
     */
    public long getSum() {
        DistributionSummary promDistSum = registry.find(descriptor.name()).tags(tagsSet).summary();
        if (promDistSum != null) {
            return (long) promDistSum.takeSnapshot().total();
        }
        return (long) summary.takeSnapshot().total();
    }

    @Override
    public Snapshot getSnapshot() {
        DistributionSummary promDistSum = registry.find(descriptor.name()).tags(tagsSet).summary();
        if (promDistSum != null) {
            return new SnapshotAdapter(promDistSum.takeSnapshot());
        }
        return new SnapshotAdapter(summary.takeSnapshot());
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
