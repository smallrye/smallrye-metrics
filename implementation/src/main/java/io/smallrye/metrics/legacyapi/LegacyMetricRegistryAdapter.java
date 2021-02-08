package io.smallrye.metrics.legacyapi;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

public class LegacyMetricRegistryAdapter implements MetricRegistry {

    private final Type type;
    private final MeterRegistry registry;

    private final Map<MetricDescriptor, MeterHolder> constructedMeters = new ConcurrentHashMap<>();
    private final Map<String, MpMetadata> metadataMap = new ConcurrentHashMap<>();

    private MemberToMetricMappings memberToMetricMappings;

    private final boolean appendScopeTags;

    public LegacyMetricRegistryAdapter(Type type, MeterRegistry registry) {
        this.type = type;
        this.registry = registry;
        if (type == Type.APPLICATION) {
            memberToMetricMappings = new MemberToMetricMappings();
        }
        appendScopeTags = ConfigProvider.getConfig().getOptionalValue("smallrye.metrics.append-scope-tags",
                Boolean.class).orElse(true);
    }

    @Override
    public <T extends Metric> T register(String name, T t) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Can not register a pre-constructed Metric with Micrometer");
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T t) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Can not register a pre-constructed Metric with Micrometer");
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T t, Tag... tags) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Can not register a pre-constructed Metric with Micrometer");
    }

    @Override
    public Counter counter(String name) {
        return internalCounter(internalGetMetadata(name, MetricType.COUNTER),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return internalCounter(internalGetMetadata(name, MetricType.COUNTER),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public Counter counter(MetricID metricID) {
        String name = metricID.getName();
        return internalCounter(internalGetMetadata(name, MetricType.COUNTER),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return internalCounter(internalGetMetadata(metadata, MetricType.COUNTER),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return internalCounter(internalGetMetadata(metadata, MetricType.COUNTER),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    Counter interceptorCounter(Metadata metadata, String... tags) {
        return internalCounter(internalGetMetadata(metadata, MetricType.COUNTER),
                new MetricDescriptor(metadata.getName(), tags));
    }

    Counter injectedCounter(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalCounter(
                internalGetMetadata(annotation.name(), MetricType.COUNTER).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    CounterAdapter internalCounter(MpMetadata metadata, MetricDescriptor id) {
        CounterAdapter result = checkCast(CounterAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new CounterAdapter()));
        return result.register(metadata, id, registry);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return internalConcurrentGauge(internalGetMetadata(name, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return internalConcurrentGauge(internalGetMetadata(name, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public ConcurrentGauge concurrentGauge(MetricID metricID) {
        String name = metricID.getName();
        return internalConcurrentGauge(internalGetMetadata(name, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return internalConcurrentGauge(internalGetMetadata(metadata, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return internalConcurrentGauge(internalGetMetadata(metadata, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    ConcurrentGaugeImpl interceptorConcurrentGauge(Metadata metadata, String... tags) {
        return internalConcurrentGauge(internalGetMetadata(metadata, MetricType.CONCURRENT_GAUGE),
                new MetricDescriptor(metadata.getName(), tags));
    }

    ConcurrentGaugeImpl injectedConcurrentGauge(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalConcurrentGauge(
                internalGetMetadata(annotation.name(), MetricType.CONCURRENT_GAUGE).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    ConcurrentGaugeImpl internalConcurrentGauge(MpMetadata metadata, MetricDescriptor id) {
        ConcurrentGaugeImpl result = checkCast(ConcurrentGaugeImpl.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new ConcurrentGaugeImpl()));
        return result.register(metadata, id, registry);
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags()), o, f);
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String name, T o, Function<T, R> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T o, Function<T, R> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags()), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T o, Function<T, R> f, Tag... tags) {
        String name = metadata.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)), o, f);
    }

    @SuppressWarnings("unchecked")
    <T> GaugeAdapter<Double> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, ToDoubleFunction<T> f) {
        GaugeAdapter.DoubleFunctionGauge<T> result = checkCast(GaugeAdapter.DoubleFunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.DoubleFunctionGauge<>(obj, f)));
        return result.register(metadata, id, registry);
    }

    @SuppressWarnings("unchecked")
    <T, R extends Number> GaugeAdapter<R> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, Function<T, R> f) {
        GaugeAdapter.FunctionGauge<T, R> result = checkCast(GaugeAdapter.FunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.FunctionGauge<>(obj, f)));
        return result.register(metadata, id, registry);
    }

    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags()), f);
    }

    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags()), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> f, Tag... tags) {
        String name = metadata.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withScopeTags(tags)), f);
    }

    @SuppressWarnings("unchecked")
    <T extends Number> GaugeAdapter<T> internalGauge(MpMetadata metadata, MetricDescriptor id, Supplier<T> f) {
        GaugeAdapter<T> result = checkCast(GaugeAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.NumberSupplierGauge<>(f)));
        return result.register(metadata, id, registry);
    }

    void bindAnnotatedGauge(AnnotatedGaugeAdapter adapter) {
        MetricDescriptor id = new MetricDescriptor(adapter.name(), adapter.tags());
        AnnotatedGaugeAdapter oops = checkCast(AnnotatedGaugeAdapter.class, adapter.getMetadata(),
                constructedMeters.putIfAbsent(id, adapter));
        if (oops == null) {
            metadataMap.put(adapter.name(), adapter.getMetadata());
            adapter.register(id, registry);
        } else {
            throw new IllegalArgumentException(
                    String.format("Gauge %s already exists. (existing='%s', new='%s')",
                            adapter.getId(), oops.getTargetName(), adapter.getTargetName()));
        }
    }

    @Override
    public Histogram histogram(String name) {
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        String name = metricID.getName();
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return internalHistogram(internalGetMetadata(metadata, MetricType.HISTOGRAM),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return internalHistogram(internalGetMetadata(metadata, MetricType.HISTOGRAM),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    HistogramAdapter injectedHistogram(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalHistogram(
                internalGetMetadata(annotation.name(), MetricType.HISTOGRAM).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    HistogramAdapter internalHistogram(MpMetadata metadata, MetricDescriptor id) {
        HistogramAdapter result = checkCast(HistogramAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new HistogramAdapter()));
        return result.register(metadata, id, registry);
    }

    @Override
    public Meter meter(String name) {
        return internalMeter(internalGetMetadata(name, MetricType.METERED),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return internalMeter(internalGetMetadata(name, MetricType.METERED),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public Meter meter(MetricID metricID) {
        String name = metricID.getName();
        return internalMeter(internalGetMetadata(name, MetricType.METERED),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return internalMeter(internalGetMetadata(metadata, MetricType.METERED),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return internalMeter(internalGetMetadata(metadata, MetricType.METERED),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    MeterAdapter injectedMeter(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalMeter(
                internalGetMetadata(annotation.name(), MetricType.METERED).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    MeterAdapter internalMeter(MpMetadata metadata, MetricDescriptor id) {
        // MP Meter --> Micrometer Counter
        MeterAdapter result = checkCast(MeterAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new MeterAdapter()));
        return result.register(metadata, id, registry);
    }

    @Override
    public Timer timer(String name) {
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public Timer timer(MetricID metricID) {
        String name = metricID.getName();
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return internalTimer(internalGetMetadata(metadata, MetricType.TIMER),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return internalTimer(internalGetMetadata(metadata, MetricType.TIMER),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    TimerAdapter injectedTimer(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalTimer(
                internalGetMetadata(annotation.name(), MetricType.TIMER).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    TimerAdapter interceptorTimer(Metadata metadata, String... tags) {
        return internalTimer(internalGetMetadata(metadata, MetricType.TIMER),
                new MetricDescriptor(metadata.getName(), tags));
    }

    TimerAdapter internalTimer(MpMetadata metadata, MetricDescriptor id) {
        TimerAdapter result = checkCast(TimerAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new TimerAdapter(registry)));
        return result.register(metadata, id);
    }

    @Override
    public SimpleTimer simpleTimer(String name) {
        return internalSimpleTimer(internalGetMetadata(name, MetricType.SIMPLE_TIMER),
                new MetricDescriptor(name, withScopeTags()));
    }

    @Override
    public SimpleTimer simpleTimer(String name, Tag... tags) {
        return internalSimpleTimer(internalGetMetadata(name, MetricType.SIMPLE_TIMER),
                new MetricDescriptor(name, withScopeTags(tags)));
    }

    @Override
    public SimpleTimer simpleTimer(MetricID metricID) {
        String name = metricID.getName();
        return internalSimpleTimer(internalGetMetadata(name, MetricType.SIMPLE_TIMER),
                new MetricDescriptor(name, withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata) {
        return internalSimpleTimer(internalGetMetadata(metadata, MetricType.SIMPLE_TIMER),
                new MetricDescriptor(metadata.getName(), withScopeTags()));
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata, Tag... tags) {
        return internalSimpleTimer(internalGetMetadata(metadata, MetricType.SIMPLE_TIMER),
                new MetricDescriptor(metadata.getName(), withScopeTags(tags)));
    }

    @Override
    public Metric getMetric(MetricID metricID) {
        return constructedMeters.get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> asType) {
        return asType
                .cast(constructedMeters
                        .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray()))));
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return (Counter) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public ConcurrentGauge getConcurrentGauge(MetricID metricID) {
        return (ConcurrentGauge) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Gauge<?> getGauge(MetricID metricID) {
        return (Gauge<?>) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return (Histogram) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Meter getMeter(MetricID metricID) {
        return (Meter) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return (Timer) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public SimpleTimer getSimpleTimer(MetricID metricID) {
        return (SimpleTimer) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withScopeTags(metricID.getTagsAsArray())));
    }

    @Override
    public Metadata getMetadata(String name) {
        return metadataMap.get(name);
    }

    TimerAdapter injectedSimpleTimer(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalSimpleTimer(
                internalGetMetadata(annotation.name(), MetricType.SIMPLE_TIMER).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    TimerAdapter internalSimpleTimer(MpMetadata metadata, MetricDescriptor id) {
        // SimpleTimer --> Micrometer Timer
        TimerAdapter result = checkCast(TimerAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new TimerAdapter(registry)));
        return result.register(metadata, id);
    }

    @Override
    public boolean remove(String name) {
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            if (e.getKey().name().equals(name)) {
                constructedMeters.remove(e.getKey());
                registry.remove(e.getValue().getMeter());
            }
        }
        return metadataMap.remove(name) != null;
    }

    @Override
    public boolean remove(MetricID metricID) {
        return internalRemove(new MetricDescriptor(metricID));
    }

    @Override
    public void removeMatching(MetricFilter metricFilter) {
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            MetricID mid = e.getKey().toMetricID();
            if (metricFilter.matches(mid, e.getValue())) {
                internalRemove(e.getKey());
            }
        }
    }

    boolean internalRemove(MetricDescriptor match) {
        MeterHolder holder = constructedMeters.remove(match);
        if (holder != null) {
            registry.remove(holder.getMeter());
            // Remove associated metadata if this is the last MP Metric left with that name
            if (constructedMeters.keySet().stream().noneMatch(id -> id.name.equals(match.name))) {
                metadataMap.remove(match.name);
            }
        }
        return holder != null;
    }

    @Override
    public SortedSet<String> getNames() {
        return new TreeSet<>(metadataMap.keySet());
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        SortedSet<MetricID> out = new TreeSet<>();
        for (MetricDescriptor key : constructedMeters.keySet()) {
            out.add(key.toMetricID());
        }
        return out;
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter metricFilter) {
        return getMetrics(MetricType.GAUGE, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter metricFilter) {
        return getMetrics(MetricType.COUNTER, metricFilter);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges() {
        return getConcurrentGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges(MetricFilter metricFilter) {
        return getMetrics(MetricType.CONCURRENT_GAUGE, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter metricFilter) {
        return getMetrics(MetricType.HISTOGRAM, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters(MetricFilter metricFilter) {
        return getMetrics(MetricType.METERED, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter metricFilter) {
        return getMetrics(MetricType.TIMER, metricFilter);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers() {
        return getSimpleTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers(MetricFilter metricFilter) {
        return getMetrics(MetricType.SIMPLE_TIMER, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Metric> getMetrics(MetricFilter filter) {
        SortedMap<MetricID, Metric> out = new TreeMap<>();
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            MetricID mid = e.getKey().toMetricID();
            if (filter.matches(mid, e.getValue())) {
                out.put(e.getKey().toMetricID(), e.getValue());
            }
        }
        return out;
    }

    @Override
    public <T extends Metric> SortedMap<MetricID, T> getMetrics(Class<T> ofType, MetricFilter filter) {
        SortedMap<MetricID, T> out = new TreeMap<>();
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            if (e.getValue().getType().equals(MetricType.from(ofType))) {
                MetricID mid = e.getKey().toMetricID();
                if (filter.matches(mid, e.getValue())) {
                    out.put(e.getKey().toMetricID(), (T) e.getValue());
                }
            }
        }
        return out;
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        SortedMap<MetricID, Metric> out = new TreeMap<>();
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            out.put(e.getKey().toMetricID(), e.getValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    <T extends Metric> SortedMap<MetricID, T> getMetrics(MetricType type, MetricFilter filter) {
        SortedMap<MetricID, T> out = new TreeMap<>();
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            if (e.getValue().getType() == type) {
                MetricID mid = e.getKey().toMetricID();
                if (filter.matches(mid, e.getValue())) {
                    out.put(e.getKey().toMetricID(), (T) e.getValue());
                }
            }
        }
        return out;
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return Collections.unmodifiableMap(metadataMap);
    }

    @Override
    public Type getType() {
        return type;
    }

    public Tags withScopeTags(Tag... tags) {
        Tags out = appendScopeTags ? Tags.of("scope", this.type.getName()) : Tags.empty();
        for (Tag t : tags) {
            out = out.and(t.getTagName(), t.getTagValue());
        }
        return out;
    }

    public Tag[] scopeTagsLegacy() {
        if (appendScopeTags) {
            return new Tag[] {
                    new Tag("scope", this.type.getName())
            };
        } else {
            return new Tag[0];
        }
    }

    private MpMetadata internalGetMetadata(String name, MetricType type) {
        MpMetadata result = metadataMap.computeIfAbsent(name, k -> new MpMetadata(name, type));
        if (result.type != type) {
            throw new IllegalStateException(
                    String.format("Metric %s already defined using a different type (%s)",
                            name, result.getType()));
        }
        return result;
    }

    private MpMetadata internalGetMetadata(Metadata metadata, MetricType type) {
        MpMetadata result = metadataMap.computeIfAbsent(metadata.getName(), k -> MpMetadata.sanitize(metadata, type));
        if (!result.mergeSameType(metadata)) {
            throw new IllegalArgumentException(
                    String.format("Metric %s already defined using a different type (%s)",
                            metadata.getName(), result.getType()));
        }
        return result;
    }

    <T> T checkCast(Class<T> type, MpMetadata metadata, MeterHolder o) {
        try {
            return type.cast(o);
        } catch (ClassCastException cce) {
            throw new IllegalStateException(
                    String.format("Metric %s already defined using a different type (%s)",
                            metadata.name, o.getType().name()),
                    cce);
        }
    }

    public MemberToMetricMappings getMemberToMetricMappings() {
        return memberToMetricMappings;
    }

}
