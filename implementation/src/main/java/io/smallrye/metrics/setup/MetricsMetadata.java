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
import org.eclipse.microprofile.metrics.annotation.Counted;
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
                //add this CDI MetricID into MetricRegistry's MetricID list....
                MetricID metricID = new MetricID(metadata.getName(),
                        appendScopeTags(tags, (LegacyMetricRegistryAdapter) registry));
                metricIDs.add(metricID);

                //Some list in MetricRegistry that maps the CDI element, metricID and metric type
                ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addMetric(element, metricID,
                        MetricType.COUNTER);
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

    //XXX: this was just to create a OriginAndMetadata.. is this needed?
    public static Metadata getMetadata(Object origin, String name, String unit, String description, String displayName,
            MetricType type) {
        Metadata metadata = Metadata.builder().withName(name).withType(type).withUnit(unit).withDescription(description)
                .withDisplayName(displayName).build();
        return new OriginAndMetadata(origin, metadata);
    }

    private static Tag[] appendScopeTags(Tag[] tags, LegacyMetricRegistryAdapter adapter) {
        return Stream.concat(Arrays.stream(tags), Arrays.stream(adapter.scopeTagsLegacy())).toArray(Tag[]::new);
    }

}
