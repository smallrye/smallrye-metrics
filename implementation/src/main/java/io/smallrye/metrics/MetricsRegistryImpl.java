/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.smallrye.metrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.InjectionPoint;

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

import io.smallrye.metrics.app.ConcurrentGaugeImpl;
import io.smallrye.metrics.app.CounterImpl;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.SimpleTimerImpl;
import io.smallrye.metrics.app.TimerImpl;

/**
 * @author hrupp
 */
@Vetoed
public class MetricsRegistryImpl implements MetricRegistry {

    private Map<String, Metadata> metadataMap = new ConcurrentHashMap<>();

    private Map<MetricID, Metric> metricMap = new ConcurrentHashMap<>();

    /*
     * this is for storing origins. until 2.0, origins were stored using OriginTrackedMetadata instead of regular metadata, but
     * since 2.0 we have to keep track of the origin per each MetricID separately, while Metadata itself
     * is only tracked per Metric Name, that's why we need two maps for that now.
     */
    private Map<MetricID, Object> originMap = new HashMap<>();

    private Type registryType;

    private MemberToMetricMappings memberToMetricMappings;

    public MetricsRegistryImpl() {
        this(null);
    }

    public MetricsRegistryImpl(Type registryType) {
        this.registryType = registryType;
        if (registryType == Type.APPLICATION) {
            memberToMetricMappings = new MemberToMetricMappings();
        }
    }

    @Override
    public synchronized <T extends Metric> T register(String name, T metric) {

        final MetricID metricID = new MetricID(name);
        if (metricMap.keySet().contains(metricID)) {
            throw SmallRyeMetricsMessages.msg.metricWithNameAlreadyExists(name);
        }

        MetricType type = inferMetricType(metric.getClass());
        if (type == null || type.equals(MetricType.INVALID)) {
            throw SmallRyeMetricsMessages.msg.unableToInferMetricType();
        }

        Metadata m = Metadata.builder().withName(name).withType(type).build();
        metricMap.put(metricID, metric);
        metadataMap.put(name, m);
        return metric;
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) {
        return register(sanitizeMetadata(metadata, metric.getClass()), metric, (Tag[]) null);
    }

    @Override
    public synchronized <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) {
        String name = metadata.getName();
        if (name == null) {
            throw SmallRyeMetricsMessages.msg.metricNameMustNotBeNullOrEmpty();
        }
        MetricID metricID = new MetricID(name, tags);
        Metadata existingMetadata = metadataMap.get(name);

        if (metricMap.containsKey(metricID) && metadata.getTypeRaw().equals(MetricType.GAUGE)) {
            throw SmallRyeMetricsMessages.msg.gaugeWithIdAlreadyExists(metricID);
        }

        /*
         * if metadata for this name already exists:
         * - if no metadata was specified for this registration, check that this metric has the same type, then reuse the
         * existing metadata instance
         * - if metadata was specified for this registration, verify that it's the same as the existing one
         * if no metadata for this name exists:
         * - if no metadata was specified for this registration, create a reasonable default
         * - if metadata was specified for this registration, use it
         */
        if (existingMetadata != null) {
            if (metadata instanceof UnspecifiedMetadata) {
                if (!metadata.getType().equals(existingMetadata.getType())) {
                    throw SmallRyeMetricsMessages.msg.metricExistsUnderDifferentType(name, existingMetadata.getType());
                }
                metricMap.put(metricID, metric);
            } else {
                verifyMetadataEquality(metadata, existingMetadata);
                metricMap.put(metricID, metric);
                if (metadata instanceof OriginAndMetadata) {
                    originMap.put(metricID, ((OriginAndMetadata) metadata).getOrigin());
                }
            }
        } else {
            if (metadata instanceof UnspecifiedMetadata) {
                Metadata realMetadata = ((UnspecifiedMetadata) metadata).convertToRealMetadata();
                metadataMap.put(name, realMetadata);
                metricMap.put(metricID, metric);
            } else {
                if (metadata instanceof OriginAndMetadata) {
                    originMap.put(metricID, ((OriginAndMetadata) metadata).getOrigin());
                    metadataMap.put(name, ((OriginAndMetadata) metadata).getMetadata());
                } else {
                    metadataMap.put(name, sanitizeMetadata(metadata, metric.getClass()));
                }
                metricMap.put(metricID, metric);
            }
        }
        return metric;
    }

    private void verifyMetadataEquality(Metadata newMetadata, Metadata existingMetadata) {
        /*
         * we could use simply an equals() call but inspecting the objects in detail allows us to
         * throw a more user-friendly error if the metadata objects are not equal
         */
        if (!existingMetadata.getTypeRaw().equals(newMetadata.getTypeRaw())) {
            throw SmallRyeMetricsMessages.msg.metricExistsUnderDifferentType(newMetadata.getName(), existingMetadata.getType());
        }

        // unspecified means that someone is programmatically obtaining a metric instance without specifying the metadata, so we check only the name and type
        if (!(newMetadata instanceof UnspecifiedMetadata)) {

            String existingUnit = existingMetadata.getUnit();
            String newUnit = newMetadata.getUnit();
            if (!existingUnit.equals(newUnit)) {
                throw SmallRyeMetricsMessages.msg.unitDiffersFromPreviousUsage(existingUnit);
            }

            String existingDescription = existingMetadata.getDescription();
            String newDescription = newMetadata.getDescription();
            if (!existingDescription.equals(newDescription)) {
                throw SmallRyeMetricsMessages.msg.descriptionDiffersFromPreviousUsage();
            }

            if (!existingMetadata.getDisplayName().equals(newMetadata.getDisplayName())) {
                throw SmallRyeMetricsMessages.msg.displayNameDiffersFromPreviousUsage();
            }
        }
    }

    @Override
    public Counter counter(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.COUNTER));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.COUNTER));
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.COUNTER));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.COUNTER));
    }

    @Override
    public Counter counter(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.COUNTER));
    }

    @Override
    public Gauge<?> gauge(String name, Gauge<?> gauge) {
        Objects.requireNonNull(gauge);
        return get(new MetricID(name), new UnspecifiedMetadata(name, MetricType.GAUGE), gauge);
    }

    @Override
    public Gauge<?> gauge(String name, Gauge<?> gauge, Tag... tags) {
        Objects.requireNonNull(gauge);
        return get(new MetricID(name, tags), new UnspecifiedMetadata(name, MetricType.GAUGE), gauge);
    }

    @Override
    public Gauge<?> gauge(MetricID metricID, Gauge<?> gauge) {
        Objects.requireNonNull(gauge);
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.GAUGE), gauge);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public Histogram histogram(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.HISTOGRAM));
    }

    @Override
    public Meter meter(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.METERED));
    }

    @Override
    public Meter meter(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.METERED));
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.METERED));
    }

    @Override
    public Timer timer(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.TIMER));
    }

    @Override
    public Timer timer(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.TIMER));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.TIMER));
    }

    @Override
    public SimpleTimer simpleTimer(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.SIMPLE_TIMER));
    }

    @Override
    public SimpleTimer simpleTimer(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.SIMPLE_TIMER));
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata) {
        return get(new MetricID(metadata.getName()), sanitizeMetadata(metadata, MetricType.SIMPLE_TIMER));
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), sanitizeMetadata(metadata, MetricType.SIMPLE_TIMER));
    }

    @Override
    public SimpleTimer simpleTimer(MetricID metricID) {
        return get(metricID, new UnspecifiedMetadata(metricID.getName(), MetricType.SIMPLE_TIMER));
    }

    private <T extends Metric> T get(MetricID metricID, Metadata metadata) {
        return get(metricID, metadata, null);
    }

    private synchronized <T extends Metric> T get(MetricID metricID, Metadata metadata, T implementor) {
        String name = metadata.getName();
        MetricType type = metadata.getTypeRaw();
        if (name == null || name.isEmpty()) {
            throw SmallRyeMetricsMessages.msg.metricNameMustNotBeNullOrEmpty();
        }

        Metadata previousMetadata = metadataMap.get(name);
        Metric previousMetric = metricMap.get(metricID);

        if (previousMetric == null) {
            Metric m;
            switch (type) {

                case COUNTER:
                    m = new CounterImpl();
                    break;
                case GAUGE:
                    m = implementor;
                    break;
                case METERED:
                    m = new MeterImpl();
                    break;
                case HISTOGRAM:
                    m = new HistogramImpl(new ExponentiallyDecayingReservoir());
                    break;
                case TIMER:
                    m = new TimerImpl(new ExponentiallyDecayingReservoir());
                    break;
                case CONCURRENT_GAUGE:
                    m = new ConcurrentGaugeImpl();
                    break;
                case SIMPLE_TIMER:
                    m = new SimpleTimerImpl();
                    break;
                case INVALID:
                default:
                    throw new IllegalStateException("Must not happen");
            }
            if (metadata instanceof OriginAndMetadata) {
                SmallRyeMetricsLogging.log.registerMetric(metricID, type,
                        ((OriginAndMetadata) metadata).getOrigin());
            } else {
                SmallRyeMetricsLogging.log.registerMetric(metricID, type);
            }

            register(metadata, m, metricID.getTagsAsList().toArray(new Tag[] {}));
        } else if (!previousMetadata.getTypeRaw().equals(metadata.getTypeRaw())) {
            throw SmallRyeMetricsMessages.msg.metricExistsUnderDifferentType(name, previousMetadata.getType());
        } else if (metadata instanceof OriginAndMetadata &&
                originMap.get(metricID) != null &&
                areCompatibleOrigins(originMap.get(metricID), ((OriginAndMetadata) metadata).getOrigin())) {
            // stop caring, same thing.
        } else {
            verifyMetadataEquality(metadata, previousMetadata);
        }

        return (T) metricMap.get(metricID);
    }

    private boolean areCompatibleOrigins(Object left, Object right) {
        if (left.equals(right)) {
            return true;
        }

        if (left instanceof InjectionPoint || right instanceof InjectionPoint) {
            return true;
        }

        return false;
    }

    @Override
    public boolean remove(String metricName) {
        SmallRyeMetricsLogging.log.removeMetricsByName(metricName);
        // iterate over all metricID's in the map and remove the ones with this name
        for (MetricID metricID : metricMap.keySet()) {
            if (metricID.getName().equals(metricName)) {
                metricMap.remove(metricID);
            }
        }
        // dispose of the metadata as well
        return metadataMap.remove(metricName) != null;
    }

    @Override
    public synchronized boolean remove(MetricID metricID) {
        if (metricMap.containsKey(metricID)) {
            SmallRyeMetricsLogging.log.removeMetricsById(metricID);
            metricMap.remove(metricID);
            // remove the metadata as well if this is the last metric of this name to be removed
            String name = metricID.getName();
            if (metricMap.keySet().stream().noneMatch(id -> id.getName().equals(name))) {
                SmallRyeMetricsLogging.log.removeMetadata(name);
                metadataMap.remove(name);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeMatching(MetricFilter metricFilter) {
        Iterator<Map.Entry<MetricID, Metric>> iterator = metricMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MetricID, Metric> entry = iterator.next();
            if (metricFilter.matches(entry.getKey(), entry.getValue())) {
                remove(entry.getKey());
            }
        }
    }

    @Override
    public java.util.SortedSet<String> getNames() {
        SortedSet<String> out = new TreeSet<>();
        for (MetricID id : metricMap.keySet()) {
            out.add(id.getName());
        }
        return out;
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return new TreeSet<>(metricMap.keySet());
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
    public java.util.SortedMap<MetricID, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public java.util.SortedMap<MetricID, Histogram> getHistograms(MetricFilter metricFilter) {
        return getMetrics(MetricType.HISTOGRAM, metricFilter);
    }

    @Override
    public java.util.SortedMap<MetricID, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public java.util.SortedMap<MetricID, Meter> getMeters(MetricFilter metricFilter) {
        return getMetrics(MetricType.METERED, metricFilter);
    }

    @Override
    public java.util.SortedMap<MetricID, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public java.util.SortedMap<MetricID, Timer> getTimers(MetricFilter metricFilter) {
        return getMetrics(MetricType.TIMER, metricFilter);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers() {
        return getSimpleTimers(MetricFilter.ALL);

    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers(MetricFilter filter) {
        return getMetrics(MetricType.SIMPLE_TIMER, filter);
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return new HashMap<>(metricMap);
    }

    @Override
    public Metric getMetric(MetricID metricID) {
        return metricMap.get(metricID);
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> asType) {
        try {
            return asType.cast(getMetric(metricID));
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(metricID + " was not of expected type " + asType, e);
        }
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return getMetric(metricID, Counter.class);
    }

    @Override
    public ConcurrentGauge getConcurrentGauge(MetricID metricID) {
        return getMetric(metricID, ConcurrentGauge.class);
    }

    @Override
    public Gauge<?> getGauge(MetricID metricID) {
        return getMetric(metricID, Gauge.class);
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return getMetric(metricID, Histogram.class);
    }

    @Override
    public Meter getMeter(MetricID metricID) {
        return getMetric(metricID, Meter.class);
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return getMetric(metricID, Timer.class);
    }

    @Override
    public SimpleTimer getSimpleTimer(MetricID metricID) {
        return getMetric(metricID, SimpleTimer.class);
    }

    @Override
    public SortedMap<MetricID, Metric> getMetrics(MetricFilter filter) {
        SortedMap<MetricID, Metric> out = new TreeMap<>();
        for (Map.Entry<MetricID, Metric> entry : metricMap.entrySet()) {
            if (filter.matches(entry.getKey(), entry.getValue())) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    @Override
    public <T extends Metric> SortedMap<MetricID, T> getMetrics(Class<T> ofType, MetricFilter filter) {
        return (SortedMap<MetricID, T>) getMetrics(
                (metricID, metric) -> filter.matches(metricID, metric)
                        && ofType.isAssignableFrom(metric.getClass()));
    }

    private Metadata sanitizeMetadata(Metadata metadata, Class<?> metricClass) {
        if (metadata.getTypeRaw() == null || metadata.getTypeRaw() == MetricType.INVALID) {
            MetricType inferredMetricType = inferMetricType(metricClass);
            return Metadata.builder(metadata).withType(inferredMetricType).build();
        } else {
            return metadata;
        }
    }

    private Metadata sanitizeMetadata(Metadata metadata, MetricType metricType) {
        // if the metadata does not specify a type, we add it here
        // if the metadata specifies a type, we check that it's the correct one
        // (for example, someone might have called registry.counter(metadata) where metadata.type="gauge")
        if (metadata.getTypeRaw() == null || metadata.getTypeRaw() == MetricType.INVALID) {
            return Metadata.builder(metadata).withType(metricType).build();
        } else {
            if (metadata.getTypeRaw() != metricType) {
                throw SmallRyeMetricsMessages.msg.typeMismatch(metricType, metadata.getTypeRaw());
            } else {
                return metadata;
            }
        }
    }

    private <T extends Metric> SortedMap<MetricID, T> getMetrics(MetricType type, MetricFilter filter) {
        SortedMap<MetricID, T> out = new TreeMap<>();

        for (Map.Entry<MetricID, Metric> entry : metricMap.entrySet()) {
            if (isSameType(entry.getValue(), type)) {
                if (filter.matches(entry.getKey(), entry.getValue())) {
                    out.put(entry.getKey(), (T) entry.getValue());
                }
            }
        }
        return out;
    }

    private boolean isSameType(Metric metricInstance, MetricType type) {
        switch (type) {
            case CONCURRENT_GAUGE:
                return metricInstance instanceof ConcurrentGauge;
            case GAUGE:
                return metricInstance instanceof Gauge;
            case HISTOGRAM:
                return metricInstance instanceof Histogram;
            case TIMER:
                return metricInstance instanceof Timer;
            case METERED:
                return metricInstance instanceof Meter;
            case COUNTER:
                return metricInstance instanceof Counter;
            case SIMPLE_TIMER:
                return metricInstance instanceof SimpleTimer;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public synchronized Map<String, Metadata> getMetadata() {
        return new HashMap<>(metadataMap);
    }

    @Override
    public Metadata getMetadata(String name) {
        return metadataMap.get(name);
    }

    @Override
    public Type getType() {
        return registryType;
    }

    public MemberToMetricMappings getMemberToMetricMappings() {
        return memberToMetricMappings;
    }

    /**
     * Guess the metric type from a class object. Recursively scans its
     * superclasses and implemented interfaces.
     * If no metric type can be inferred, returns null.
     * If multiple metric types are inferred, throws an IllegalArgumentException.
     */
    private MetricType inferMetricType(Class<?> clazz) {
        MetricType direct = metricTypeFromClass(clazz);
        if (direct != null) {
            return direct;
        } else {
            MetricType candidateType = null;
            // recursively scan the superclass first, then implemented interfaces
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && !superClass.equals(Object.class)) {
                candidateType = inferMetricType(superClass);
            }
            for (Class<?> implementedInterface : clazz.getInterfaces()) {
                MetricType newCandidateType = inferMetricType(implementedInterface);
                if (candidateType == null) {
                    candidateType = newCandidateType;
                } else {
                    if (newCandidateType != null && !candidateType.equals(newCandidateType)) {
                        throw SmallRyeMetricsMessages.msg.ambiguousMetricType(newCandidateType, candidateType);
                    }
                }
            }
            return candidateType;
        }
    }

    private MetricType metricTypeFromClass(Class<?> in) {
        if (in.equals(Counter.class)) {
            return MetricType.COUNTER;
        } else if (in.equals(Gauge.class)) {
            return MetricType.GAUGE;
        } else if (in.equals(ConcurrentGauge.class)) {
            return MetricType.CONCURRENT_GAUGE;
        } else if (in.equals(Meter.class)) {
            return MetricType.METERED;
        } else if (in.equals(Timer.class)) {
            return MetricType.TIMER;
        } else if (in.equals(SimpleTimer.class)) {
            return MetricType.SIMPLE_TIMER;
        } else if (in.equals(Histogram.class)) {
            return MetricType.HISTOGRAM;
        }
        return null;
    }
}
