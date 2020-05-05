/*
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.interceptors;

import java.lang.annotation.Annotation;
import java.util.Collections;

import javax.enterprise.inject.Vetoed;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.TagsUtils;
import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.BeanInfo;
import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.elementdesc.MemberType;

@Vetoed
public class MetricResolver {

    private MetricName metricName = new SeMetricName(Collections.emptySet());

    public Of<Counted> counted(BeanInfo topClass, MemberInfo element) {
        return resolverOf(topClass, element, Counted.class);
    }

    public Of<ConcurrentGauge> concurrentGauge(BeanInfo topClass, MemberInfo element) {
        return resolverOf(topClass, element, ConcurrentGauge.class);
    }

    public Of<Gauge> gauge(BeanInfo topClass, MemberInfo method) {
        return resolverOf(topClass, method, Gauge.class);
    }

    public Of<Metered> metered(BeanInfo topClass, MemberInfo element) {
        return resolverOf(topClass, element, Metered.class);
    }

    public Of<Timed> timed(BeanInfo bean, MemberInfo element) {
        return resolverOf(bean, element, Timed.class);
    }

    public Of<SimplyTimed> simplyTimed(BeanInfo bean, MemberInfo element) {
        return resolverOf(bean, element, SimplyTimed.class);
    }

    private <T extends Annotation> Of<T> resolverOf(BeanInfo bean, MemberInfo element, Class<T> metric) {
        if (element.isAnnotationPresent(metric)) {
            return elementResolverOf(element, metric);
        } else {
            return beanResolverOf(element, metric, bean);
        }
    }

    private <T extends Annotation> Of<T> elementResolverOf(MemberInfo element, Class<T> metric) {
        AnnotationInfo annotation = element.getAnnotation(metric);
        String name = metricName(element, metric, annotation.name(), annotation.absolute());
        Tag[] tags = metricTags(annotation);
        return new DoesHaveMetric<>(annotation, name, tags);
    }

    private <T extends Annotation> Of<T> beanResolverOf(MemberInfo element, Class<T> metric, BeanInfo bean) {
        if (bean.isAnnotationPresent(metric)) {
            AnnotationInfo annotation = bean.getAnnotation(metric);
            String name = metricName(bean, element, metric, annotation.name(), annotation.absolute());
            Tag[] tags = metricTags(annotation);
            return new DoesHaveMetric<>(annotation, name, tags);
        } else if (bean.getSuperclass() != null) {
            return beanResolverOf(element, metric, bean.getSuperclass());
        }
        return new DoesNotHaveMetric<>();
    }

    // TODO: should be grouped with the metric name strategy
    private String metricName(MemberInfo element, Class<? extends Annotation> type, String name, boolean absolute) {
        String metric = name.isEmpty() ? defaultName(element, type) : metricName.of(name);
        return absolute ? metric : MetricRegistry.name(element.getDeclaringClassName(), metric);
    }

    private String metricName(BeanInfo bean, MemberInfo element, Class<? extends Annotation> type, String name,
            boolean absolute) {
        String metric = name.isEmpty() ? bean.getSimpleName() : metricName.of(name);
        return absolute ? MetricRegistry.name(metric, defaultName(element, type))
                : MetricRegistry.name(bean.getPackageName(), metric, defaultName(element, type));
    }

    private String defaultName(MemberInfo element, Class<? extends Annotation> type) {
        return memberName(element);
    }

    // While the Member Javadoc states that the getName method should returns
    // the simple name of the underlying member or constructor, the FQN is returned
    // for constructors. See JDK-6294399:
    // http://bugs.java.com/view_bug.do?bug_id=6294399
    private String memberName(MemberInfo member) {
        if (member.getMemberType() == MemberType.CONSTRUCTOR) {
            return member.getDeclaringClassSimpleName();
        } else {
            return member.getName();
        }
    }

    private Tag[] metricTags(AnnotationInfo annotation) {
        return TagsUtils.parseTagsAsArray(annotation.tags());
    }

    public interface Of<T extends Annotation> {

        boolean isPresent();

        String metricName();

        Tag[] tags();

        AnnotationInfo metricAnnotation();
    }

    private static final class DoesHaveMetric<T extends Annotation> implements Of<T> {

        private final AnnotationInfo annotation;

        private final String name;

        private final Tag[] tags;

        private DoesHaveMetric(AnnotationInfo annotation, String name, Tag[] tags) {
            this.annotation = annotation;
            this.name = name;
            this.tags = tags;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String metricName() {
            return name;
        }

        @Override
        public Tag[] tags() {
            return tags;
        }

        @Override
        public AnnotationInfo metricAnnotation() {
            return annotation;
        }
    }

    @Vetoed
    private static final class DoesNotHaveMetric<T extends Annotation> implements Of<T> {

        private DoesNotHaveMetric() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public String metricName() {
            throw SmallRyeMetricsMessages.msg.noMetricPresent();
        }

        @Override
        public Tag[] tags() {
            throw SmallRyeMetricsMessages.msg.noMetricPresent();
        }

        @Override
        public AnnotationInfo metricAnnotation() {
            throw SmallRyeMetricsMessages.msg.noMetricPresent();
        }
    }
}
