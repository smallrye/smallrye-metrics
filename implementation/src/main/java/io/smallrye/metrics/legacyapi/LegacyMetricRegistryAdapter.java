package io.smallrye.metrics.legacyapi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.setup.ApplicationNameResolver;

public class LegacyMetricRegistryAdapter implements MetricRegistry {

    private final Type type;
    private final MeterRegistry registry;

    protected static final String MP_APPLICATION_NAME_TAG = "mp_app";

    protected static final String MP_APPLICATION_NAME_VAR = "mp.metrics.appName";

    private final Map<MetricDescriptor, MeterHolder> constructedMeters = new ConcurrentHashMap<>();
    private final Map<String, MpMetadata> metadataMap = new ConcurrentHashMap<>();

    /**
     * This ConcurrentHashMap<String,Tag> holds the cached value of the MP Config mp.metrics.appName value for each appliation.
     */
    protected final ConcurrentHashMap<String, io.micrometer.core.instrument.Tag> applicationMPConfigAppNameTagCache;

    protected final ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricID>> applicationMap;

    protected final ApplicationNameResolver appNameResolver;

    private MemberToMetricMappings memberToMetricMappings;

    /**
     * This static Tag[] represents the server level global tags retrieved from MP Config for mp.metrics.appName. This value
     * will be 'null' when not initialized. If during
     * initialization and no global tag has been resolved this will be to an array of size 0. Using an array of size 0 is to
     * represent that an attempt was made to
     * resolve the value, but none was found. As the MP Config mp.metrics.appName is retrieved each time register/retrieve is
     * used. This helps with performance tremendously.
     *
     * This server level value will not change at all throughout the life time of the server as it is defined by env vars or sys
     * props.
     */
    protected static io.micrometer.core.instrument.Tag[] SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG = null;

    public MeterRegistry getPrometheusMeterRegistry() {
        return registry;
    }

    /**
     * Adds the MetricID to an application map.
     * This map is not a complete list of metrics owned by an application,
     * produced metrics are managed in the MetricsExtension
     *
     * @param name
     */
    protected void addNameToApplicationMap(MetricID metricID) {
        String appName = appNameResolver.getApplicationName();
        addNameToApplicationMap(metricID, appName);
    }

    /**
     * Adds the MetricID to an application map given the application name.
     * This map is not a complete list of metrics owned by an application,
     * produced metrics are managed in the MetricsExtension
     *
     * @param metricID metric ID of metric that was added
     * @param appName applicationName
     */
    public void addNameToApplicationMap(MetricID metricID, String appName) {
        // If it is a base metric, the name will be null
        if (appName == null)
            return;
        ConcurrentLinkedQueue<MetricID> list = applicationMap.get(appName);
        if (list == null) {
            ConcurrentLinkedQueue<MetricID> newList = new ConcurrentLinkedQueue<MetricID>();
            list = applicationMap.putIfAbsent(appName, newList);
            if (list == null)
                list = newList;
        }
        list.add(metricID);
    }

    public void unRegisterApplicationMetrics() {
        unRegisterApplicationMetrics(appNameResolver.getApplicationName());
    }

    public void unRegisterApplicationMetrics(String appName) {
        /*
         * This would be the case if the ApplicationListener30's ApplicatinInfo does not contain
         * the application's deployment name (corrupt application?) or if this MetricRegistry is
         * not running under the application TCCL ( as it relies on the ComponentMetadata to
         * retrieve the application name).
         */
        if (appName == null) {

            //TODO: SR logging instead
            //Tr.event(tc, "Application name is null. Cannot unregister metrics for null application.");

            //XXX: dev debug
            System.out.println("Application name is null. Cannot unregister metrics for null application.");
            return;
        }

        ConcurrentLinkedQueue<MetricID> list = applicationMap.remove(appName);

        if (list != null) {
            for (MetricID metricID : list) {
                remove(metricID);
            }
        }
    }

    public LegacyMetricRegistryAdapter(Type type, MeterRegistry registry, ApplicationNameResolver appNameResolver) {
        this.appNameResolver = (appNameResolver == null) ? ApplicationNameResolver.DEFAULT : appNameResolver;
        this.type = type;
        this.registry = registry;

        this.applicationMPConfigAppNameTagCache = new ConcurrentHashMap<String, io.micrometer.core.instrument.Tag>();

        applicationMap = new ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricID>>();

        if (type == Type.APPLICATION) {
            memberToMetricMappings = new MemberToMetricMappings();
        }
    }

    /**
     *
     * @param tags the application tags to be merged with the MP Config mp.metrics.appName tag
     * @return combined Tag array of the MP Config mp.metrics.appName tag with application tags; can return null
     */
    private Tags combineApplicationTagsWithMPConfigAppNameTag(Tags tags) {
        return combineApplicationTagsWithMPConfigAppNameTag(false, tags);
    }

    /**
     *
     * @param sorted boolean to choose if the Tag array returned is sorted by key or not
     * @param tags the application tags to be merged with the MP Config mp.metrics.appName tag
     * @return combined Tag array of the MP Config mp.metrics.appName tag with application tags; can return null
     */
    private Tags combineApplicationTagsWithMPConfigAppNameTag(boolean isSorted, Tags tags) {
        io.micrometer.core.instrument.Tag mpConfigAppTag = resolveMPConfigAppNameTag();
        Map<String, String> tagMap = (isSorted) ? new TreeMap<String, String>() : new HashMap<String, String>();
        if (mpConfigAppTag != null) {
            tagMap.put(mpConfigAppTag.getKey(), mpConfigAppTag.getValue());

            /*
             * Application Metric tags are put into the map second
             * this will over write any conflicting tags. This is similar
             * to the old behaviour when MetricID auto-resolved MP Config tags
             * it would resolve MP COnfig tags first then add application tags
             */
            for (io.micrometer.core.instrument.Tag tag : tags) {
                tagMap.put(tag.getKey(), tag.getValue());
            }

            Tags result = Tags.empty();
            for (Entry<String, String> entry : tagMap.entrySet()) {
                result = result.and(entry.getKey(), entry.getValue());
            }

            tags = result;

        }
        return tags;

    }

    /**
     * This method will retrieve cached tag values for the mp.metrics.appName or resolve it and cache it
     *
     * @return The application level MP Config mp.metrics.appName tag of the application; Or if it exists the server level
     *         value; Or null
     */
    private synchronized io.micrometer.core.instrument.Tag resolveMPConfigAppNameTag() {

        String appName = appNameResolver.getApplicationName();

        /*
         * If appName is null then we aren't running in an application context.
         * This is possible when resolving metrics for BASE or VENDOR.
         *
         * Since we're using a ConcurrentHashMap, can't store a null key and don't want
         * to risk making up a key a user might use as their appName. So we'll call two methods
         * that are similar. resolveAppTagByServer() will, however, store to a static array.
         *
         */
        io.micrometer.core.instrument.Tag tag = (appName == null) ? resolveMPConfigAppNameTagByServer()
                : resolveMPConfigAppNameTagByApplication(appName);
        return tag;
    }

    /**
     * This will return server level application tag
     * i.e defined in env var or sys props
     *
     * Will return null if no MP Config value is set
     * for the mp.metrics.appName on the server level
     *
     * @return Tag The server wide application tag; can return null
     */
    private synchronized io.micrometer.core.instrument.Tag resolveMPConfigAppNameTagByServer() {
        if (SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG == null) {
            SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG = new io.micrometer.core.instrument.Tag[1];

            //Using MP Config to retreive the mp.metrics.appName Config value
            Optional<String> applicationName = ConfigProvider.getConfig().getOptionalValue(MP_APPLICATION_NAME_VAR,
                    String.class);

            //Evaluate if there exists a tag value or set tag[0] to be null for no value;
            SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG[0] = (applicationName.isPresent())
                    ? io.micrometer.core.instrument.Tag.of(MP_APPLICATION_NAME_TAG,
                            applicationName.get())
                    : null;
        }
        return SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG[0];
    }

    /**
     * This will return the MP Config value for
     * mp.metrics.appName for the application
     * that the current TCCL is running for
     *
     * @param appName the application name to look up from cache
     * @return Tag The mp.metrics.appName MP Config value associated to the appName; can return null if non exists
     */
    private synchronized io.micrometer.core.instrument.Tag resolveMPConfigAppNameTagByApplication(String appName) {
        //Return cached value
        if (!applicationMPConfigAppNameTagCache.containsKey(appName)) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            //Using MP Config to retreive the mp.metrics.appName Config value
            Optional<String> applicationName = ConfigProvider.getConfig(classLoader).getOptionalValue(MP_APPLICATION_NAME_VAR,
                    String.class);

            /*
             * Evaluate if there exists a tag value. If there is not then we must create an "invalid" Tag to represent no value
             * resolved.
             * This is used later to return a null value.
             * This is due the use of ConcurrentHashMap and we cannot set a null key.
             */
            io.micrometer.core.instrument.Tag appTag = (applicationName.isPresent())
                    ? io.micrometer.core.instrument.Tag.of(MP_APPLICATION_NAME_TAG,
                            applicationName.get())
                    : io.micrometer.core.instrument.Tag.of("null",
                            "null");

            //Cache the value
            applicationMPConfigAppNameTagCache.put(appName, appTag);
        }

        //Perhaps we don't really need a concurrent hashmap.. so we can avoid this.
        io.micrometer.core.instrument.Tag returnTag;
        return ((returnTag = applicationMPConfigAppNameTagCache.get(appName)).getKey().equals("null")) ? null : returnTag;
    }

    public LegacyMetricRegistryAdapter(Type type, MeterRegistry registry) {
        this(type, registry, ApplicationNameResolver.DEFAULT);
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
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return internalCounter(internalGetMetadata(name, MetricType.COUNTER),
                new MetricDescriptor(name, withAppTags(tags)));
    }

    @Override
    public Counter counter(MetricID metricID) {
        String name = metricID.getName();
        return internalCounter(internalGetMetadata(name, MetricType.COUNTER),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return internalCounter(internalGetMetadata(metadata, MetricType.COUNTER),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return internalCounter(internalGetMetadata(metadata, MetricType.COUNTER),
                new MetricDescriptor(metadata.getName(), withAppTags(tags)));
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
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return null;
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return null;
    }

    @Override
    public ConcurrentGauge concurrentGauge(MetricID metricID) {
        return null;
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return null;
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return null;
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags()), o, f);
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags(tags)), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String name, T o, Function<T, R> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags(tags)), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T o, Function<T, R> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags()), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T o, Function<T, R> f, Tag... tags) {
        String name = metadata.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags(tags)), o, f);
    }

    @SuppressWarnings("unchecked")
    <T> GaugeAdapter<Double> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, ToDoubleFunction<T> f) {
        GaugeAdapter.DoubleFunctionGauge<T> result = checkCast(GaugeAdapter.DoubleFunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.DoubleFunctionGauge<>(obj, f)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry);
    }

    @SuppressWarnings("unchecked")
    <T, R extends Number> GaugeAdapter<R> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, Function<T, R> f) {
        GaugeAdapter.FunctionGauge<T, R> result = checkCast(GaugeAdapter.FunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.FunctionGauge<>(obj, f)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry);
    }

    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags()), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f, Tag... tags) {
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags(tags)), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags()), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> f, Tag... tags) {
        String name = metadata.getName();
        return internalGauge(internalGetMetadata(name, MetricType.GAUGE),
                new MetricDescriptor(name, withAppTags(tags)), f);
    }

    @SuppressWarnings("unchecked")
    <T extends Number> GaugeAdapter<T> internalGauge(MpMetadata metadata, MetricDescriptor id, Supplier<T> f) {
        GaugeAdapter<T> result = checkCast(GaugeAdapter.NumberSupplierGauge.class, metadata,
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
            throw new IllegalArgumentException(String.format("Gauge %s already exists. (existing='%s', new='%s')",
                    adapter.getId(), oops.getTargetName(), adapter.getTargetName()));
        }
    }

    @Override
    public Histogram histogram(String name) {
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withAppTags(tags)));
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        String name = metricID.getName();
        return internalHistogram(internalGetMetadata(name, MetricType.HISTOGRAM),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return internalHistogram(internalGetMetadata(metadata, MetricType.HISTOGRAM),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return internalHistogram(internalGetMetadata(metadata, MetricType.HISTOGRAM),
                new MetricDescriptor(metadata.getName(), withAppTags(tags)));
    }

    HistogramAdapter injectedHistogram(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalHistogram(
                internalGetMetadata(annotation.name(), MetricType.HISTOGRAM).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    HistogramAdapter internalHistogram(MpMetadata metadata, MetricDescriptor id) {
        HistogramAdapter result = checkCast(HistogramAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new HistogramAdapter()));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry);
    }

    @Override
    public Meter meter(String name) {
        return null;
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return null;
    }

    @Override
    public Meter meter(MetricID metricID) {
        return null;
    }

    @Override
    public Meter meter(Metadata metadata) {
        return null;
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return null;
    }

    @Override
    public Timer timer(String name) {
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withAppTags(tags)));
    }

    @Override
    public Timer timer(MetricID metricID) {
        String name = metricID.getName();
        return internalTimer(internalGetMetadata(name, MetricType.TIMER),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return internalTimer(internalGetMetadata(metadata, MetricType.TIMER),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return internalTimer(internalGetMetadata(metadata, MetricType.TIMER),
                new MetricDescriptor(metadata.getName(), withAppTags(tags)));
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
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id);
    }

    @Override
    public SimpleTimer simpleTimer(String name) {
        return null;
    }

    @Override
    public SimpleTimer simpleTimer(String name, Tag... tags) {
        return null;
    }

    @Override
    public SimpleTimer simpleTimer(MetricID metricID) {
        return null;
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata) {
        return null;
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata, Tag... tags) {
        return null;
    }

    @Override
    public Metric getMetric(MetricID metricID) {
        return constructedMeters.get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> asType) {
        return asType
                .cast(constructedMeters.get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray()))));
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return (Counter) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public ConcurrentGauge getConcurrentGauge(MetricID metricID) {
        return (ConcurrentGauge) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Gauge<?> getGauge(MetricID metricID) {
        return (Gauge<?>) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return (Histogram) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Meter getMeter(MetricID metricID) {
        return (Meter) constructedMeters.get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return (Timer) constructedMeters.get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public SimpleTimer getSimpleTimer(MetricID metricID) {
        return (SimpleTimer) constructedMeters
                .get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
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

            //XXX: Since we've been registering to the Global Reg, we need to 
            //remove from global reg... This involves using the Thread Locals again.
            ThreadLocal<Boolean> tlb = null;
            if (type.equals(Type.APPLICATION)) {
                tlb = MetricRegistries.MP_APP_METER_REG_ACCESS;
            } else if (type.equals(Type.BASE)) {
                tlb = MetricRegistries.MP_BASE_METER_REG_ACCESS;
            } else {
                tlb = MetricRegistries.MP_VENDOR_METER_REG_ACCESS;
            }
            tlb.set(true);
            //io.micrometer.core.instrument.Meter meter = registry.remove(holder.getMeter());
            io.micrometer.core.instrument.Meter meter = Metrics.globalRegistry.remove(holder.getMeter());
            tlb.set(false);

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

    public Tags withAppTags(Tag... tags) {

        Tags out = Tags.empty();

        for (Tag t : tags) {
            out = out.and(t.getTagName(), t.getTagValue());
        }

        out = combineApplicationTagsWithMPConfigAppNameTag(out);

        return out;
    }

    public Tag[] scopeTagsLegacy() {
        return new Tag[] { new Tag("scope", this.type.getName()) };
    }

    private MpMetadata internalGetMetadata(String name, MetricType type) {
        MpMetadata result = metadataMap.computeIfAbsent(name, k -> new MpMetadata(name, type));
        if (result.type != type) {
            throw new IllegalStateException(String.format("Metric %s already defined using a different type (%s)",
                    name, result.getType()));
        }
        //TODO: Checked type.. Check other aspects of metadata? Or are we not strict?
        return result;
    }

    private MpMetadata internalGetMetadata(Metadata metadata, MetricType type) {
        MpMetadata result = metadataMap.computeIfAbsent(metadata.getName(), k -> MpMetadata.sanitize(metadata, type));

        if (!result.mergeSameType(metadata)) {
            throw new IllegalArgumentException(String.format("Metric %s already defined using a different type (%s)",
                    metadata.getName(), result.getType()));
        }

        if (!result.equals(MpMetadata.sanitize(metadata, type))) {
            throw new IllegalArgumentException(
                    String.format("Existing metadata (%s) does not match with supplied metadata (%s)",
                            result.toString(), metadata.toString()));
        }
        //TODO: Checked type.. Check other aspects of metadata? Or are we not strict?
        return result;
    }

    <T> T checkCast(Class<T> type, MpMetadata metadata, MeterHolder o) {
        try {
            return type.cast(o);
        } catch (ClassCastException cce) {
            throw new IllegalStateException(String.format("Metric %s already defined using a different type (%s)",
                    metadata.name, o.getType().name()), cce);
        }
    }

    public MemberToMetricMappings getMemberToMetricMappings() {
        return memberToMetricMappings;
    }

}
