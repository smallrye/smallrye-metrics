package io.smallrye.metrics.legacyapi;

import org.eclipse.microprofile.metrics.MetricType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.SharedMetricRegistries;

class CounterAdapter implements org.eclipse.microprofile.metrics.Counter, MeterHolder {

    Counter counter;

    public CounterAdapter register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        //if we're creating a new counter... or we're "updating" an existing one with new metadata (but this doesnt actually register with micrometer)
        if (counter == null || metadata.cleanDirtyMetadata()) {
            counter = Counter.builder(descriptor.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(descriptor.tags())
                    .tags("scope", scope)
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
    public long getCount() {
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
