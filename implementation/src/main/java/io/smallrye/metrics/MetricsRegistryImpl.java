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

import io.smallrye.metrics.app.ConcurrentGaugeImpl;
import io.smallrye.metrics.app.CounterImpl;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.TimerImpl;

import java.util.SortedSet;
import java.util.TreeSet;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.jboss.logging.Logger;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.InjectionPoint;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author hrupp
 */
@Vetoed
public class MetricsRegistryImpl extends MetricRegistry {

    private static final Logger log = Logger.getLogger(MetricsRegistryImpl.class);
    private static final String NONE = "NONE";

    private Map<String, Metadata> metadataMap = new HashMap<>();
    private Map<MetricID, Metric> metricMap = new ConcurrentHashMap<>();

    Optional<String> globalTags;

    public MetricsRegistryImpl() {

        Config c  = org.eclipse.microprofile.config.ConfigProvider.getConfig();

        // TODO
        // TODO find out if this dance is still needed in newer versions of config
        globalTags = c.getOptionalValue("mp.metrics.tags", String.class);
        if (globalTags.isPresent()) {
            return;
        }
        globalTags = c.getOptionalValue("MP_METRICS_TAGS",String.class);

    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {

        final MetricID metricID = new MetricID(name);
        if (metricMap.keySet().contains(metricID)) {
            throw new IllegalArgumentException("A metric with name " + name + " already exists");
        }

        MetricType type;
        Class<?> metricCls = metric.getClass();
        if (metricCls.getName().contains("Lambda")) {
            String tname = metricCls.getGenericInterfaces()[0].getTypeName(); // TODO [0] is brittle
            tname = tname.substring(tname.lastIndexOf('.') + 1);
            tname = tname.toLowerCase();
            type = MetricType.from(tname);
        } else if (metricCls.isAnonymousClass()) {
            type = MetricType.from(metricCls.getInterfaces().length == 0 ? metricCls.getSuperclass().getInterfaces()[0] : metricCls.getInterfaces()[0]);
        } else {
            if (!metricCls.isInterface()) {
                // [0] is ok, as all our Impl classes implement exactly the one matching interface
                type = MetricType.from(metricCls.getInterfaces()[0]);
            } else {
                type = MetricType.from(metricCls);
            }
        }

        Metadata m = Metadata.builder().withName(name).withType(type).build();
        metricMap.put(metricID, metric);
        metadataMap.put(name, m);
        return metric;
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(metadata, metric, (Tag[]) null);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        String name = metadata.getName();
        if (name == null) {
            throw new IllegalArgumentException("Metric name must not be null");
        }
        MetricID metricID = new MetricID(name, tags);
        Metadata existingMetadata = metadataMap.get(name);

        boolean reusableFlag = (existingMetadata == null || existingMetadata.isReusable());

        //Gauges are not reusable
        if (metadata.getTypeRaw().equals(MetricType.GAUGE)) {
            reusableFlag = false;
        }

        if (metricMap.keySet().contains(metricID) && !reusableFlag) {
            throw new IllegalArgumentException("A metric with metricID " + metricID + " already exists");
        }

        /* if metadata for this name already exists:
               - if no metadata was specified for this registration, check that this metric has the same type, then reuse the existing metadata instance
               - if metadata was specified for this registration, verify that it's the same as the existing one
           if no metadata for this name exists:
               - if no metadata was specified for this registration, create a reasonable default
               - if metadata was specified for this registration, use it
             */
        if(existingMetadata != null) {
            if(metadata instanceof UnspecifiedMetadata) {
                if(!metadata.getType().equals(existingMetadata.getType())) {
                    throw new IllegalArgumentException("There is an existing metric with name " + name + " but of different type (" + existingMetadata.getType()+")");
                }
                metricMap.put(metricID, metric);
            } else {
                verifyMetadataEquality(metadata, existingMetadata);
                metricMap.put(metricID, metric);
            }
        } else {
            if(metadata instanceof UnspecifiedMetadata) {
                Metadata realMetadata = ((UnspecifiedMetadata)metadata).convertToRealMetadata();
                metadataMap.put(name, realMetadata);
                metricMap.put(metricID, metric);
            } else {
                metadataMap.put(name, metadata);
                metricMap.put(metricID, metric);
            }
        }
        return metric;
    }

    private void verifyMetadataEquality(Metadata newMetadata, Metadata existingMetadata) {
        /*
            we could use simply an equals() call but inspecting the objects in detail allows us to
            throw a more user-friendly error if the metadata objects are not equal
         */
        if (!existingMetadata.getTypeRaw().equals(newMetadata.getTypeRaw())) {
            throw new IllegalArgumentException("Passed metric type does not match existing type");
        }

        if (existingMetadata.isReusable() != newMetadata.isReusable()) {
            throw new IllegalArgumentException("Reusable flag differs from previous usage");
        }

        String existingUnit = existingMetadata.getUnit().orElse("none");
        String newUnit = newMetadata.getUnit().orElse("none");
        if (!existingUnit.equals(newUnit)) {
            throw new IllegalArgumentException("Unit is different from the unit in previous usage (" + existingUnit +")");
        }

        String existingDescription = existingMetadata.getUnit().orElse("none");
        String newDescription = newMetadata.getUnit().orElse("none");
        if (!existingDescription.equals(newDescription)) {
            throw new IllegalArgumentException("Description differs from previous usage");
        }

        if(!existingMetadata.getDisplayName().equals(newMetadata.getDisplayName())) {
            throw new IllegalArgumentException("Display name differs from previous usage");
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
        return get(new MetricID(metadata.getName(), tags), metadata);
    }

    @Override
    public Counter counter(Metadata metadata) {
        return get(new MetricID(metadata.getName()), metadata);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return get(new MetricID(metadata.getName()), metadata);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), metadata);
    }

    @Override
    public Histogram histogram(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return get(new MetricID(metadata.getName()), metadata);
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), metadata);
    }

    @Override
    public Meter meter(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return get(new MetricID(metadata.getName()), metadata);
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), metadata);
    }

    @Override
    public Timer timer(String name) {
        return get(new MetricID(name),
                new UnspecifiedMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return get(new MetricID(metadata.getName()), metadata);
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return get(new MetricID(name, tags),
                new UnspecifiedMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return get(new MetricID(metadata.getName(), tags), metadata);
    }

    // TODO all above methods where applicable, should assert that the type (in the method name) is the same as the type in the metadata
    // TODO -- not only type, but also name
    private <T extends Metric> T get(MetricID metricID, Metadata metadata) {
        String name = metadata.getName();
        MetricType type = metadata.getTypeRaw();
        log.debugf("Get metric [id: %s, type: %s]", metricID, type);
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name must not be null or empty");
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
                    throw new IllegalArgumentException("Gauge " + name + " was not registered, this should not happen");
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
                case INVALID:
                default:
                    throw new IllegalStateException("Must not happen");
            }
            log.infof("Register metric [metricId: %s, type: %s]", metricID, type);
            register(metadata, m, metricID.getTagsAsList().toArray(new Tag[]{}));
        } else if (!previousMetadata.getTypeRaw().equals(metadata.getTypeRaw())) {
            throw new IllegalArgumentException("Previously registered metric " + name + " is of type "
                    + previousMetadata.getType() + ", expected " + metadata.getType());
        } else if ( haveCompatibleOrigins(previousMetadata, metadata)) {
            // stop caring, same thing.
        } else if (previousMetadata.isReusable() && !metadata.isReusable()) {
            throw new IllegalArgumentException("Previously registered metric " + name + " was flagged as reusable, while current request is not.");
        } else if (!previousMetadata.isReusable()) {
            throw new IllegalArgumentException("Previously registered metric " + name + " was not flagged as reusable");
        }

        return (T) metricMap.get(metricID);
    }

    private boolean haveCompatibleOrigins(Metadata left, Metadata right) {
        if ( left instanceof OriginTrackedMetadata && right instanceof OriginTrackedMetadata ) {
            OriginTrackedMetadata leftOrigin = (OriginTrackedMetadata) left;
            OriginTrackedMetadata rightOrigin = (OriginTrackedMetadata) right;

            if ( leftOrigin.getOrigin().equals(rightOrigin.getOrigin())) {
                return true;
            }

            if ( leftOrigin.getOrigin() instanceof InjectionPoint || ((OriginTrackedMetadata) right).getOrigin() instanceof InjectionPoint ) {
                return true;
            }

        }

        return false;

    }

    @Override
    public boolean remove(String metricName) {
        log.infof("Removing metrics with [name: %s]", metricName);
        // iterate over all metricID's in the map and remove the ones with this name
        for (MetricID metricID : metricMap.keySet()) {
            if(metricID.getName().equals(metricName)) {
                metricMap.remove(metricID);
            }
        }
        // dispose of the metadata as well
        return metadataMap.remove(metricName) != null;
    }

    @Override
    public boolean remove(MetricID metricID) {
        if (metricMap.containsKey(metricID)) {
            log.infof("Remove metric with [id: %s]", metricID);
            metricMap.remove(metricID);
            // remove the metadata as well if this is the last metric of this name to be removed
            if(metricMap.keySet().stream().noneMatch(id -> id.getName().equals(metricID.getName()))) {
                log.debugf("Remove metadata for [name: %s]", metricID.getName());
                metadataMap.remove(metricID.getName());
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
    public Map<MetricID, Metric> getMetrics() {

        return new HashMap<>(metricMap);
    }

    private <T extends Metric> SortedMap<MetricID, T> getMetrics(MetricType type, MetricFilter filter) {
        SortedMap<MetricID, T> out = new TreeMap<>();

        for (Map.Entry<MetricID, Metric> entry : metricMap.entrySet()) {
            Metadata metadata = metadataMap.get(entry.getKey().getName());
            if (metadata.getTypeRaw() == type) {
                if (filter.matches(entry.getKey(), entry.getValue())) {
                    out.put(entry.getKey(), (T) entry.getValue());
                }
            }
        }

        return out;
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return new HashMap<>(metadataMap);
    }
}
