package io.smallrye.metrics.legacyapi;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

class TimerAdapter implements org.eclipse.microprofile.metrics.Timer, MeterHolder {

    private static final String CLASS_NAME = TimerAdapter.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private final static int PRECISION;

    Timer globalCompositeTimer;

    /*
     * Increasing the percentile precision for timers will consume more memory.
     * This setting is "3" by default, and provided to adjust the precision to
     * your needs.
     */
    static {
        PRECISION = ConfigProvider.getConfig().getOptionalValue("mp.metrics.smallrye.timer.precision", Integer.class).orElse(3);
        LOGGER.logp(Level.FINE, CLASS_NAME, null,
                "Resolved MicroProfile Config value for mp.metrics.smallrye.timer.precision as \"{0}\"", PRECISION);
    }

    final MeterRegistry registry;

    TimerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    public TimerAdapter register(MpMetadata metadata, MetricDescriptor descriptor, String scope, Tag... globalTags) {

        if (globalCompositeTimer == null || metadata.cleanDirtyMetadata()) {

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

            globalCompositeTimer = Timer.builder(descriptor.name())
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(PRECISION)
                    .register(Metrics.globalRegistry);
        }

        return this;
    }

    public void update(long l, TimeUnit timeUnit) {
        globalCompositeTimer.record(l, timeUnit);
    }

    @Override
    public void update(Duration duration) {
        globalCompositeTimer.record(duration);
    }

    @Override
    public <T> T time(Callable<T> callable) throws Exception {
        return globalCompositeTimer.wrap(callable).call();
    }

    @Override
    public void time(Runnable runnable) {
        globalCompositeTimer.wrap(runnable).run();
    }

    @Override
    public SampleAdapter time() {
        return new SampleAdapter(globalCompositeTimer, Timer.start(Metrics.globalRegistry));
    }

    @Override
    public Duration getElapsedTime() {
        return Duration.ofNanos((long) globalCompositeTimer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public long getCount() {
        return globalCompositeTimer.count();
    }

    @Override
    public Snapshot getSnapshot() {
        return new SnapshotAdapter(globalCompositeTimer.takeSnapshot());
    }

    @Override
    public Meter getMeter() {
        return globalCompositeTimer;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(globalCompositeTimer);
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
}
