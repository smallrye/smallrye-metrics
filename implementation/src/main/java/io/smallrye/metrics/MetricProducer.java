package io.smallrye.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;

import io.micrometer.core.instrument.Tags;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricsExtension;
import io.smallrye.metrics.legacyapi.TagsUtils;
import io.smallrye.metrics.legacyapi.interceptors.MetricName;
import io.smallrye.metrics.legacyapi.interceptors.SeMetricName;

@ApplicationScoped
public class MetricProducer {

    private static final String CLASS_NAME = MetricProducer.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    MetricRegistry registry;

    MetricName metricName = new SeMetricName(Collections.emptySet());

    @Inject
    LegacyMetricsExtension metricExtension;

    /**
     * No-arg for CDI
     */
    public MetricProducer() {

    }

    /**
     * Used to create a MetricProducer with a provided LegacyMetricExtension which
     * would be typically provided by injection as seen above. This constructor is
     * for runtimes that may need to proxy the MetricProducer.
     *
     * @param metricExtension a LegacyMetricsExtension object
     */
    public MetricProducer(LegacyMetricsExtension metricExtension) {
        this.metricExtension = metricExtension;
    }

    @Produces
    public <T extends Number> Gauge<T> getGauge(InjectionPoint ip) {

        final String METHOD_NAME = "getGauge";

        // A forwarding Gauge must be returned as the Gauge creation happens when the
        // declaring bean gets instantiated and the corresponding Gauge can be injected
        // before which leads to producing a null value
        return () -> {

            registry = SharedMetricRegistries.getOrCreate(getScope(ip));
            SortedMap<MetricID, Gauge> gauges = registry.getGauges();

            String name = metricName.of(ip);
            Tag[] tags = getTags(ip);

            Tag[] mpTagArray = resolveAppNameTag(tags);

            MetricID metricID = new MetricID(name, mpTagArray);

            Gauge<T> gauge = gauges.get(metricID);

            if (gauge == null) {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "Could not retrieve gauge with MetricID [id: {0}]",
                        metricID);
                return null;
            } else {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.logp(Level.FINER, CLASS_NAME, METHOD_NAME, "Produced Gauge with MetricID [id: {0}]", metricID);
                }
                return gauge.getValue();
            }

        };
    }

    @Produces
    public Counter getCounter(InjectionPoint ip) {

        final String METHOD_NAME = "getCounter";

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));
        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Counter counter = registry.counter(metadata, tags);

        Tag[] mpTagArray = resolveAppNameTag(tags);
        MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
        metricExtension.addMetricId(metricID);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.logp(Level.FINER, CLASS_NAME, METHOD_NAME, "Produced Counter with MetricID [id: {0}]", metricID);
        }
        return counter;
    }

    @Produces
    public Timer getTimer(InjectionPoint ip) {

        final String METHOD_NAME = "getTimer";

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));

        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Timer timer = registry.timer(metadata, tags);

        Tag[] mpTagArray = resolveAppNameTag(tags);
        MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
        metricExtension.addMetricId(metricID);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.logp(Level.FINER, CLASS_NAME, METHOD_NAME, "Produced Timer with MetricID [id: {0}]", metricID);
        }
        return timer;
    }

    @Produces
    public Histogram getHistogram(InjectionPoint ip) {

        final String METHOD_NAME = "getHistogram";

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));
        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Histogram histogram = registry.histogram(metadata, tags);

        Tag[] mpTagArray = resolveAppNameTag(tags);
        MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
        metricExtension.addMetricId(metricID);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.logp(Level.FINEST, CLASS_NAME, METHOD_NAME, "Produced histogram with MetricID [id: {0}]", "");
        }
        return histogram;
    }

    private Metadata getMetadata(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        Metadata metadata;
        if (metric != null) {
            Metadata actualMetadata = Metadata.builder().withName(metricName.of(ip)).withUnit(metric.unit())
                    .withDescription(metric.description()).build();
            metadata = new OriginAndMetadata(ip, actualMetadata);
        } else {
            Metadata actualMetadata = Metadata.builder().withName(metricName.of(ip)).withUnit(MetricUnits.NONE)
                    .withDescription("").build();
            metadata = new OriginAndMetadata(ip, actualMetadata);
        }

        return metadata;
    }

    private String getScope(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        if (metric != null) {
            return metric.scope();
        } else {
            return MetricRegistry.APPLICATION_SCOPE;
        }
    }

    private Tag[] getTags(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        if (metric != null && metric.tags().length > 0) {
            return TagsUtils.parseTagsAsArray(metric.tags());
        } else {
            return new Tag[0];
        }
    }

    /*
     * Combine tags with mp.metrics.appName tag if if available to
     * provide accurate MetricIDs to the MetricID set held in the
     * CDI extension
     */
    private Tag[] resolveAppNameTag(Tag... tags) {
        Tags mmTags = ((LegacyMetricRegistryAdapter) registry).withAppTags(tags);

        List<Tag> mpListTags = new ArrayList<Tag>();
        mmTags.forEach(tag -> {
            Tag mpTag = new Tag(tag.getKey(), tag.getValue());
            mpListTags.add(mpTag);
        });

        return mpListTags.toArray(new Tag[0]);
    }

}
