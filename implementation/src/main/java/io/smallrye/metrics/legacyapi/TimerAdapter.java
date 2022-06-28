package io.smallrye.metrics.legacyapi;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.smallrye.metrics.MetricRegistries;

class TimerAdapter implements org.eclipse.microprofile.metrics.Timer, MeterHolder {
    final MeterRegistry registry;
    Timer timer;

    // which MP metric type this adapter represents - this is needed because the same class is used as an adapter for Timer and SimpleTimer
    // if this is actually a SimpleTimer, this value will be changed to reflect that
    MetricType metricType = MetricType.TIMER;

    TimerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    public TimerAdapter register(MpMetadata metadata, MetricDescriptor descriptor) {
        MetricRegistries.MP_APP_METER_REG_ACCESS.set(true);
        if (timer == null || metadata.cleanDirtyMetadata()) {
            timer = Timer
                    .builder(descriptor.name())
                    .description(metadata.getDescription())
                    .tags(descriptor.tags())
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }
        MetricRegistries.MP_APP_METER_REG_ACCESS.set(false);
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
        timer.wrap(runnable);
    }

    @Override
    public SampleAdapter time() {
        return new SampleAdapter(timer, Timer.start(registry));
    }

    @Override
    public Duration getElapsedTime() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
        //
        //timer.takeSnapshot.total();
    }

    @Override
    public long getCount() {
        return timer.count();
    }

    @Override
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
        return metricType;
    }
}
