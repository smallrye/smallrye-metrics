package io.smallrye.metrics.setup;

import static io.smallrye.metrics.legacyapi.TagsUtils.parseTagsAsArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.OriginAndMetadata;
import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.BeanInfo;
import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.legacyapi.interceptors.MetricResolver;

public class MetricsMetadata {

    private MetricsMetadata() {
    }

    public static List<MetricID> registerMetrics(MetricRegistry registry, MetricResolver resolver, BeanInfo bean,
            MemberInfo element) {
        MetricResolver.Of<Counted> counted = resolver.counted(bean, element);
        List<MetricID> metricIDs = new ArrayList<>();
        if (counted.isPresent()) {
            AnnotationInfo t = counted.metricAnnotation();
            Metadata metadata = getMetadata(element, counted.metricName(), t.unit(), t.description(), t.displayName(),
                    MetricType.COUNTER);
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.counter(metadata, tags);
            if (registry instanceof LegacyMetricRegistryAdapter) {
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.COUNTER);
            }
        }
        MetricResolver.Of<ConcurrentGauge> concurrentGauge = resolver.concurrentGauge(bean, element);
        if (concurrentGauge.isPresent()) {
            AnnotationInfo t = concurrentGauge.metricAnnotation();
            Metadata metadata = getMetadata(element, concurrentGauge.metricName(), t.unit(), t.description(), t.displayName(),
                    MetricType.CONCURRENT_GAUGE);
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.concurrentGauge(metadata, tags);
            if (registry instanceof LegacyMetricRegistryAdapter) {
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.CONCURRENT_GAUGE);
            }
        }
        MetricResolver.Of<Metered> metered = resolver.metered(bean, element);
        if (metered.isPresent()) {
            AnnotationInfo t = metered.metricAnnotation();
            Metadata metadata = getMetadata(element, metered.metricName(), t.unit(), t.description(), t.displayName(),
                    MetricType.METERED);
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.meter(metadata, tags);
            if (registry instanceof LegacyMetricRegistryAdapter) {
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.METERED);
            }
        }
        MetricResolver.Of<SimplyTimed> simplyTimed = resolver.simplyTimed(bean, element);
        if (simplyTimed.isPresent()) {
            AnnotationInfo t = simplyTimed.metricAnnotation();
            Metadata metadata = getMetadata(element, simplyTimed.metricName(), t.unit(), t.description(), t.displayName(),
                    MetricType.SIMPLE_TIMER);
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.simpleTimer(metadata, tags);
            if (registry instanceof LegacyMetricRegistryAdapter) {
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.SIMPLE_TIMER);
            }
        }
        MetricResolver.Of<Timed> timed = resolver.timed(bean, element);
        if (timed.isPresent()) {
            AnnotationInfo t = timed.metricAnnotation();
            Metadata metadata = getMetadata(element, timed.metricName(), t.unit(), t.description(), t.displayName(),
                    MetricType.TIMER);
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.timer(metadata, tags);
            if (registry instanceof LegacyMetricRegistryAdapter) {
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.TIMER);
            }
        }

        return metricIDs;
    }

    public static Metadata getMetadata(Object origin, String name, String unit, String description, String displayName,
            MetricType type) {
        Metadata metadata = Metadata.builder().withName(name)
                .withType(type)
                .withUnit(unit)
                .withDescription(description)
                .withDisplayName(displayName)
                .build();
        return new OriginAndMetadata(origin, metadata);
    }

    private static Tag[] appendScopeTags(Tag[] tags, LegacyMetricRegistryAdapter adapter) {
        return Stream.concat(Arrays.stream(tags), Arrays.stream(adapter.scopeTagsLegacy()))
                .toArray(Tag[]::new);
    }

}
