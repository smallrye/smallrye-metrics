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
import io.smallrye.metrics.interceptors.MetricResolver;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;

import static io.smallrye.metrics.TagsUtils.parseTagsAsArray;

public class MetricsMetadata {

    private MetricsMetadata() {
    }

    public static <E extends Member & AnnotatedElement> void registerMetrics(MetricRegistry registry, MetricResolver resolver, Class<?> bean, E element) {
        MetricResolver.Of<Counted> counted = resolver.counted(bean, element);
        if (counted.isPresent()) {
            Counted t = counted.metricAnnotation();
            Metadata metadata = getMetadata(t, counted.metricName(), t.unit(), t.description(), t.displayName(), MetricType.COUNTER, t.reusable());
            registry.counter(metadata, parseTagsAsArray(t.tags()));
        }
        MetricResolver.Of<ConcurrentGauge> concurrentGauge = resolver.concurrentGauge(bean, element);
        if (concurrentGauge.isPresent()) {
            ConcurrentGauge t = concurrentGauge.metricAnnotation();
            Metadata metadata = getMetadata(t, concurrentGauge.metricName(), t.unit(), t.description(), t.displayName(), MetricType.CONCURRENT_GAUGE, t.reusable());
            registry.concurrentGauge(metadata, parseTagsAsArray(t.tags()));
        }
        MetricResolver.Of<Metered> metered = resolver.metered(bean, element);
        if (metered.isPresent()) {
            Metered t = metered.metricAnnotation();
            Metadata metadata = getMetadata(t, metered.metricName(), t.unit(), t.description(), t.displayName(), MetricType.METERED, t.reusable());
            registry.meter(metadata, parseTagsAsArray(t.tags()));
        }
        MetricResolver.Of<Timed> timed = resolver.timed(bean, element);
        if (timed.isPresent()) {
            Timed t = timed.metricAnnotation();
            Metadata metadata = getMetadata(t, timed.metricName(), t.unit(), t.description(), t.displayName(), MetricType.TIMER, t.reusable());
            registry.timer(metadata, parseTagsAsArray(t.tags()));
        }
    }

    public static Metadata getMetadata(Object origin, String name, String unit, String description, String displayName, MetricType type, boolean reusable) {
        return new OriginTrackedMetadata(origin, name, type, unit, description, displayName, reusable);
    }

}
