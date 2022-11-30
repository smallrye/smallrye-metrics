package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

class FunctionCounterAdapter<T> implements org.eclipse.microprofile.metrics.Counter, MeterHolder {

    FunctionCounter globalCompositeFunctionCounter;

    final T obj;
    final ToDoubleFunction<T> function;

    public FunctionCounterAdapter(T obj, ToDoubleFunction<T> function) {
        this.obj = obj;
        this.function = function;
    }

    public FunctionCounterAdapter<T> register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry,
            String scope, Tag... globalTags) {

        // if we're creating a new counter... or we're "updating" an existing one with
        // new metadata (but this doesn't actually register with micrometer)
        if (globalCompositeFunctionCounter == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }

            if (globalTags != null) {
                for (Tag t : globalTags) {
                    tagsSet.add(t);
                }
            }

            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            globalCompositeFunctionCounter = FunctionCounter
                    .builder(descriptor.name, obj, obj -> function.applyAsDouble(obj))
                    .description(metadata.getDescription()).baseUnit(metadata.getUnit()).tags(tagsSet)
                    .register(Metrics.globalRegistry);

        }
        return this;
    }

    @Override
    public void inc() {
        throw new UnsupportedOperationException("Method must not be called");
    }

    @Override
    public void inc(long l) {
        throw new UnsupportedOperationException("Method must not be called");
    }

    @Override
    public long getCount() {
        return (long) globalCompositeFunctionCounter.count();
    }

    @Override
    public Meter getMeter() {
        return globalCompositeFunctionCounter;
    }
}
