package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

class CounterAdapter implements org.eclipse.microprofile.metrics.Counter, MeterHolder {

    Counter globalCompositeCounter;

    public CounterAdapter register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry,
            String scope) {

        // if we're creating a new counter... or we're "updating" an existing one with
        // new metadata (but this doesn't actually register with micrometer)
        if (globalCompositeCounter == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            globalCompositeCounter = Counter.builder(descriptor.name()).description(metadata.getDescription())
                    .baseUnit(metadata.getUnit()).tags(tagsSet).register(Metrics.globalRegistry);

        }
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
        return (long) globalCompositeCounter.count();
    }

    @Override
    public Meter getMeter() {
        return globalCompositeCounter;
    }
}
