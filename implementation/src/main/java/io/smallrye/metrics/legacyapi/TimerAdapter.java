package io.smallrye.metrics.legacyapi;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.smallrye.metrics.SharedMetricRegistries;

class TimerAdapter implements org.eclipse.microprofile.metrics.Timer, MeterHolder {
    Timer timer;

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
    final MeterRegistry registry;
    MetricDescriptor descriptor;
    Set<Tag> tagsSet = new HashSet<Tag>();

    TimerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    public TimerAdapter register(MpMetadata metadata, MetricDescriptor descriptor, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        if (timer == null || metadata.cleanDirtyMetadata()) {

            this.descriptor = descriptor;
            tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            timer = Timer
                    .builder(descriptor.name())
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }
        threadLocal.set(false);
        return this;
    }

    public void update(long l, TimeUnit timeUnit) {
        timer.record(l, timeUnit);
    }

    @Override
    public void update(Duration duration) {
        timer.record(duration);
    }

    @Override
    public <T> T time(Callable<T> callable) throws Exception {
        return timer.wrap(callable).call();
    }

    @Override
    public void time(Runnable runnable) {
        timer.wrap(runnable).run();
    }

    @Override
    public SampleAdapter time() {
        return new SampleAdapter(timer, Timer.start(Metrics.globalRegistry));
    }

    @Override
    /*
     * Due to registries that deny regsitration returning no-op and the chance of the composite meter
     * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
     */
    public Duration getElapsedTime() {
        Timer promTimer = registry.find(descriptor.name()).tags(tagsSet).timer();
        if (promTimer != null) {
            return Duration.ofNanos((long) promTimer.totalTime(TimeUnit.NANOSECONDS));
        }
        return Duration.ofNanos((long) timer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    /*
     * Due to registries that deny registration returning no-op and the chance of the composite meter
     * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
     */
    public long getCount() {
        Timer promTimer = registry.find(descriptor.name()).tags(tagsSet).timer();
        if (promTimer != null) {
            return (long) promTimer.count();
        }

        return timer.count();
    }

    @Override
    /** TODO: Separate Issue/PR impl Snapshot adapter */
    public Snapshot getSnapshot() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
    }

    @Override
    public Meter getMeter() {
        return timer;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(timer);
    }

    class SampleAdapter implements org.eclipse.microprofile.metrics.Timer.Context {
        final Timer timer;
        final Timer.Sample sample;

        SampleAdapter(Timer timer, Timer.Sample sample) {
            this.sample = sample;
            this.timer = timer;
        }

        @Override
        public long stop() {
            return sample.stop(timer);
        }

        @Override
        public void close() {
            sample.stop(timer);
        }
    }

    @Override
    public MetricType getType() {
        return MetricType.TIMER;
    }
}
