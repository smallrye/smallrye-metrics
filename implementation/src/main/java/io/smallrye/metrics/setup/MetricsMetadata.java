/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
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
 *
 */
package io.smallrye.metrics.setup;

import io.smallrye.metrics.OriginTrackedMetadata;
import io.smallrye.metrics.Tag;
import io.smallrye.metrics.interceptors.MetricResolver;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;

public class MetricsMetadata {

    private MetricsMetadata() {
    }

    public static <E extends Member & AnnotatedElement> void registerMetrics(MetricRegistry registry, MetricResolver resolver, Class<?> bean, E element) {
        MetricResolver.Of<Counted> counted = resolver.counted(bean, element);
        if (counted.isPresent()) {
            Counted t = counted.metricAnnotation();
            Metadata metadata = getMetadata(t, counted.metricName(), t.unit(), t.description(), t.displayName(), MetricType.COUNTER, t.reusable(), t.tags());
            registry.counter(metadata);
        }
        MetricResolver.Of<Metered> metered = resolver.metered(bean, element);
        if (metered.isPresent()) {
            Metered t = metered.metricAnnotation();
            Metadata metadata = getMetadata(t, metered.metricName(), t.unit(), t.description(), t.displayName(), MetricType.METERED, t.reusable(), t.tags());
            registry.meter(metadata);
        }
        MetricResolver.Of<Timed> timed = resolver.timed(bean, element);
        if (timed.isPresent()) {
            Timed t = timed.metricAnnotation();
            Metadata metadata = getMetadata(t, timed.metricName(), t.unit(), t.description(), t.displayName(), MetricType.TIMER, t.reusable(), t.tags());
            registry.timer(metadata);
        }
    }

    public static Metadata getMetadata(Object origin, String name, String unit, String description, String displayName, MetricType type, boolean reusable, String... tags) {
        Map<String,String> tagMap = new HashMap<>();
        if (tags != null && tags.length > 0) {
            for (String tag : tags) {
                Tag t = new Tag(tag);
                tagMap.put(t.getKey(),t.getValue());
            }
        }

        Metadata metadata = new OriginTrackedMetadata(origin, name, type, unit, description, displayName, reusable,
                                                      tagMap);
        return metadata;
    }

}
