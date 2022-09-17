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
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.setup.ApplicationNameResolver;

public class LegacyMetricRegistryAdapter implements MetricRegistry {

    private final String scope;
    private final MeterRegistry registry;

    public static final String MP_APPLICATION_NAME_TAG = "mp_app";

    public static final String MP_SCOPE_TAG = "mp_scope";

    protected static final String MP_APPLICATION_NAME_VAR = "mp.metrics.appName";

    private final Map<MetricDescriptor, MeterHolder> constructedMeters = new ConcurrentHashMap<>();
    private final Map<String, MpMetadata> metadataMap = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, io.micrometer.core.instrument.Tag> applicationMPConfigAppNameTagCache;

    protected final ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricID>> applicationMap;

    protected final ApplicationNameResolver appNameResolver;

    private MemberToMetricMappings memberToMetricMappings;

    protected static io.micrometer.core.instrument.Tag[] SERVER_LEVEL_MPCONFIG_APPLICATION_NAME_TAG = null;

    public MeterRegistry getPrometheusMeterRegistry() {
        return registry;
    }

    public void addNameToApplicationMap(MetricID metricID) {
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

    public LegacyMetricRegistryAdapter(String scope, MeterRegistry registry, ApplicationNameResolver appNameResolver) {
        this.appNameResolver = (appNameResolver == null) ? ApplicationNameResolver.DEFAULT : appNameResolver;
        this.scope = scope;
        this.registry = registry;

        this.applicationMPConfigAppNameTagCache = new ConcurrentHashMap<String, io.micrometer.core.instrument.Tag>();

        applicationMap = new ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricID>>();

        if (scope != BASE_SCOPE && scope != VENDOR_SCOPE) {
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
     * @param isSorted boolean to choose if the Tag array returned is sorted by key or not
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

    public LegacyMetricRegistryAdapter(String scope, MeterRegistry registry) {
        this(scope, registry, ApplicationNameResolver.DEFAULT);
    }

    @Override
    public Counter counter(String name) {
        return internalCounter(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalCounter(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags));
    }

    @Override
    public Counter counter(MetricID metricID) {
        String name = metricID.getName();
        return internalCounter(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return internalCounter(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalCounter(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), unifiedTags));
    }

    Counter interceptorCounter(Metadata metadata, String... tags) {
        return internalCounter(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), tags));
    }

    Counter injectedCounter(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalCounter(
                internalGetMetadata(annotation.name()).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    private void validateTagNamesMatch(MetricDescriptor id) {
        /*
         * Check that if there are metrics registered with same metric name that the tag
         * set is the same. Specifically that the tag key values are the same. Values
         * can differ.
         */
        for (MetricDescriptor md : constructedMeters.keySet()) {
            if (md.name().equals(id.name) && !id.isTagNamesMatch(md.tags())) {
                throw new IllegalArgumentException(String.format(
                        "The set of tags names provided do not match those of an existing metric with the same name. Provided = %s Existing = %s ",
                        id.tags.toString(), md.tags.toString()));
            }
        }
    }

    CounterAdapter internalCounter(MpMetadata metadata, MetricDescriptor id) {

        validateTagNamesMatch(id);

        CounterAdapter result = checkCast(CounterAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new CounterAdapter()));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry, scope);
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f) {
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()), o, f);
    }

    public <T> Gauge<Double> gauge(String name, T o, ToDoubleFunction<T> f, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String name, T o, Function<T, R> f, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T o, Function<T, R> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()), o, f);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T o, Function<T, R> f, Tag... tags) {
        String name = metadata.getName();
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalGauge(internalGetMetadata(metadata),
                new MetricDescriptor(name, unifiedTags), o, f);
    }

    @SuppressWarnings("unchecked")
    <T> GaugeAdapter<Double> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, ToDoubleFunction<T> f) {
        validateTagNamesMatch(id);
        GaugeAdapter.DoubleFunctionGauge<T> result = checkCast(GaugeAdapter.DoubleFunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.DoubleFunctionGauge<>(obj, f)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry, scope);
    }

    @SuppressWarnings("unchecked")
    <T, R extends Number> GaugeAdapter<R> internalGauge(MpMetadata metadata, MetricDescriptor id, T obj, Function<T, R> f) {
        validateTagNamesMatch(id);
        GaugeAdapter.FunctionGauge<T, R> result = checkCast(GaugeAdapter.FunctionGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.FunctionGauge<>(obj, f)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry, scope);
    }

    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f) {
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> f, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> f) {
        String name = metricID.getName();
        return internalGauge(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()), f);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> f, Tag... tags) {
        String name = metadata.getName();
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalGauge(internalGetMetadata(metadata),
                new MetricDescriptor(name, unifiedTags), f);
    }

    @SuppressWarnings("unchecked")
    <T extends Number> GaugeAdapter<T> internalGauge(MpMetadata metadata, MetricDescriptor id, Supplier<T> f) {
        validateTagNamesMatch(id);
        GaugeAdapter<T> result = checkCast(GaugeAdapter.NumberSupplierGauge.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new GaugeAdapter.NumberSupplierGauge<T>(f)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry, scope);
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
        return internalHistogram(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalHistogram(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags));
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        String name = metricID.getName();
        return internalHistogram(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return internalHistogram(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalHistogram(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), unifiedTags));
    }

    HistogramAdapter injectedHistogram(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalHistogram(
                internalGetMetadata(annotation.name()).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    HistogramAdapter internalHistogram(MpMetadata metadata, MetricDescriptor id) {
        validateTagNamesMatch(id);
        HistogramAdapter result = checkCast(HistogramAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new HistogramAdapter()));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, registry, scope);
    }

    @Override
    public Timer timer(String name) {
        return internalTimer(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags()));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalTimer(internalGetMetadata(name),
                new MetricDescriptor(name, unifiedTags));
    }

    @Override
    public Timer timer(MetricID metricID) {
        String name = metricID.getName();
        return internalTimer(internalGetMetadata(name),
                new MetricDescriptor(name, withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return internalTimer(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), withAppTags()));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        /*
         * Verify tags before internalGetMetadata().
         * The call withAppTags() can throw an IAE.
         * Don't want to have had created metadata
         * and have it put into the map before that.
         */
        Tags unifiedTags = withAppTags(tags);
        return internalTimer(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), unifiedTags));
    }

    TimerAdapter injectedTimer(org.eclipse.microprofile.metrics.annotation.Metric annotation) {
        return internalTimer(
                internalGetMetadata(annotation.name()).merge(annotation),
                new MetricDescriptor(annotation.name(), annotation.tags()));
    }

    TimerAdapter interceptorTimer(Metadata metadata, String... tags) {
        return internalTimer(internalGetMetadata(metadata),
                new MetricDescriptor(metadata.getName(), tags));
    }

    TimerAdapter internalTimer(MpMetadata metadata, MetricDescriptor id) {
        validateTagNamesMatch(id);
        TimerAdapter result = checkCast(TimerAdapter.class, metadata,
                constructedMeters.computeIfAbsent(id, k -> new TimerAdapter(registry)));
        addNameToApplicationMap(id.toMetricID());
        return result.register(metadata, id, scope);
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
    public Timer getTimer(MetricID metricID) {
        return (Timer) constructedMeters.get(new MetricDescriptor(metricID.getName(), withAppTags(metricID.getTagsAsArray())));
    }

    @Override
    public Metadata getMetadata(String name) {
        return metadataMap.get(name);
    }

    @Override
    public boolean remove(String name) {

        boolean isRemoveSuccess = false;
        for (Map.Entry<MetricDescriptor, MeterHolder> e : constructedMeters.entrySet()) {
            if (e.getKey().name().equals(name)) {
                isRemoveSuccess = internalRemove(e.getKey());
            }
        }
        return isRemoveSuccess;

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
            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);

            threadLocal.set(true);
            io.micrometer.core.instrument.Meter meter = Metrics.globalRegistry.remove(holder.getMeter());
            threadLocal.set(false);

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
        return getMetrics(Gauge.class, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter metricFilter) {
        return getMetrics(Counter.class, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter metricFilter) {
        return getMetrics(Histogram.class, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter metricFilter) {
        return getMetrics(Timer.class, metricFilter);
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
            if (ofType.isAssignableFrom(e.getValue().getClass())) {
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

    @Override
    public Map<String, Metadata> getMetadata() {
        return Collections.unmodifiableMap(metadataMap);
    }

    @Override
    public String getScope() {
        return scope;
    }

    /**
     * Must be called before any internalGetMetadata calls
     * We may throw an IllegalArgumentException. So we don't
     * want metadata to be registered if it was not necessary.
     * 
     * 
     * @param tags
     * @return
     */
    public Tags withAppTags(Tag... tags) {

        Tags out = Tags.empty();

        if (tags != null) {
            for (Tag t : tags) {
                /*
                 * Need to check if tags being passed in are
                 * 'mp_scope' or 'mp_app'; throw IAE as per spec
                 * 
                 * mp_scope is provided to micrometer registry
                 * during metric/meter registration in the adapters
                 * 
                 * mp_app is resolved with the resolveMPConfigAppNameTag()
                 * logic
                 */
                if (t.getTagName().equals(MP_APPLICATION_NAME_TAG)
                        || t.getTagName().equals(MP_SCOPE_TAG)) {
                    throw new IllegalArgumentException("Can not use "
                            + "reserved tag names: \"mp_scope\" "
                            + "or \"mp_app\"");
                }
                out = out.and(t.getTagName(), t.getTagValue());
            }
        }

        out = combineApplicationTagsWithMPConfigAppNameTag(out);

        return out;
    }

    public Tag[] scopeTagsLegacy() {
        return new Tag[] { new Tag("scope", this.scope) };
    }

    private MpMetadata internalGetMetadata(String name) {

        MpMetadata result = metadataMap.computeIfAbsent(name, k -> new MpMetadata(name));

        /*
         * Check that metadata of metric being registered/retrieved matches existing
         * existing metadata (if it exists)
         */
        if (!result.equals(MpMetadata.sanitize(new MpMetadata(name)))) {
            throw new IllegalArgumentException(
                    String.format("Existing metadata (%s) does not match with supplied metadata (%s)",
                            result.toString(), new MpMetadata(name).toString()));
        }

        return result;
    }

    private MpMetadata internalGetMetadata(Metadata metadata) {
        MpMetadata result = metadataMap.computeIfAbsent(metadata.getName(), k -> MpMetadata.sanitize(metadata));

        /*
         * Check that metadata of metric being registered/retrieved matches existing
         * existing metadata (if it exists)
         */
        if (!result.equals(MpMetadata.sanitize(metadata))) {
            throw new IllegalArgumentException(
                    String.format("Existing metadata (%s) does not match with supplied metadata (%s)",
                            result.toString(), metadata.toString()));
        }

        return result;
    }

    <T> T checkCast(Class<T> type, MpMetadata metadata, MeterHolder o) {
        try {
            return type.cast(o);
        } catch (ClassCastException cce) {
            throw new IllegalStateException(String.format("Metric %s already defined using a different type (%s)",
                    metadata.name, o.getMeter().getId().getType()), cce);
        }
    }

    public MemberToMetricMappings getMemberToMetricMappings() {
        return memberToMetricMappings;
    }

}
