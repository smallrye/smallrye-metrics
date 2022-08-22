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

    /*
     * Due to multiple Prometheus meter registries being registered to the global
     * composite meter registry with deny filters used, this can lead to a problem
     * when the composite meter is retrieving a value of the meter. It will chose
     * the "first" meter registry associated to the composite meter. This meter
     * registry may have returned a Noop meter (due it being denied). As a result,
     * querying this composite meter for a value can return a 0.
     * 
     * We keep acquire the Prometheus meter registry's meter and use it to retrieve
     * values. Can't just acquire the meter during value retrieval due to situation
     * where if this meter(holder) was removed from the MP shim, the application
     * code could still have reference to this object and can still perform a get
     * value calls.
     * 
     * We keep the global composite meter as this is what is "used" when we need to
     * remove this meter. The composite meter's object ref is used to remove from
     * the global composite registry.
     * 
     * See SharedMetricRegistries.java for more information.
     * 
     */

    Counter globalCompositeCounter;
    Counter promCounter;

    public CounterAdapter register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry,
            String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        // if we're creating a new counter... or we're "updating" an existing one with
        // new metadata (but this doesn't actually register with micrometer)
        if (globalCompositeCounter == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            globalCompositeCounter = Counter.builder(descriptor.name()).description(metadata.getDescription())
                    .baseUnit(metadata.getUnit()).tags(tagsSet).register(Metrics.globalRegistry);

            /*
             * Due to registries that deny registration returning no-op and the chance of
             * the composite meter obtaining the no-oped meter, we need to acquire
             * Prometheus meter registry's copy of this meter/metric.
             * 
             * Save this and use it to retrieve values.
             */
            promCounter = registry.find(descriptor.name()).tags(tagsSet).counter();
            if (promCounter == null) {
                promCounter = globalCompositeCounter;
                // TODO: logging?
            }
        }
        threadLocal.set(false);
        return this;
    }

    @Override
    public void inc() {
        globalCompositeCounter.increment();
    }

    @Override
    public void inc(long l) {
        globalCompositeCounter.increment(l);
    }

    @Override
    public long getCount() {
        return (long) promCounter.count();
    }

    @Override
    public Meter getMeter() {
        return globalCompositeCounter;
    }

    @Override
    public MetricType getType() {
        return MetricType.COUNTER;
    }
}
