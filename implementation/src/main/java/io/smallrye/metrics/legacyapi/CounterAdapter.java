package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.MetricType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

class CounterAdapter implements org.eclipse.microprofile.metrics.Counter, MeterHolder {

    Counter counter;

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

    public CounterAdapter register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        //if we're creating a new counter... or we're "updating" an existing one with new metadata (but this doesnt actually register with micrometer)
        if (counter == null || metadata.cleanDirtyMetadata()) {

            this.registry = registry;
            this.descriptor = descriptor;

            tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            counter = Counter.builder(descriptor.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .register(Metrics.globalRegistry);
        }

        threadLocal.set(false);
        return this;
    }

    @Override
    public void inc() {
        counter.increment();
    }

    @Override
    public void inc(long l) {
        counter.increment(l);
    }

    @Override
    /*
     * Due to registries that deny regsitration returning no-op and the chance of the composite meter
     * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
     */
    public long getCount() {
        Counter promCounter = registry.find(descriptor.name()).tags(tagsSet).counter();
        if (promCounter != null) {
            return (long) promCounter.count();
        }
        return (long) counter.count();
    }

    @Override
    public Meter getMeter() {
        return counter;
    }

    @Override
    public MetricType getType() {
        return MetricType.COUNTER;
    }
}
