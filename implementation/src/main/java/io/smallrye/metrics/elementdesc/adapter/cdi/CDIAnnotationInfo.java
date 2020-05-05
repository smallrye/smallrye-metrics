/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.annotation.Annotation;

import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.AnnotationInfo;

public class CDIAnnotationInfo implements AnnotationInfo {

    private final Annotation annotation;

    CDIAnnotationInfo(Annotation annotation) {
        this.annotation = annotation;
    }

    @Override
    public String name() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).name();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).name();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).name();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).name();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).name();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).name();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }

    @Override
    public boolean absolute() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).absolute();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).absolute();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).absolute();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).absolute();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).absolute();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).absolute();
        } else {
            throw SmallRyeMetricsMessages.msg.unknownMetricAnnotationType(annotation.annotationType());
        }
    }

    @Override
    public String[] tags() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).tags();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).tags();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).tags();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).tags();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).tags();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).tags();
        } else {
            throw SmallRyeMetricsMessages.msg.unknownMetricAnnotationType(annotation.annotationType());
        }
    }

    @Override
    public String unit() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).unit();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).unit();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).unit();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).unit();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).unit();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).unit();
        } else {
            throw SmallRyeMetricsMessages.msg.unknownMetricAnnotationType(annotation.annotationType());
        }
    }

    @Override
    public String description() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).description();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).description();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).description();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).description();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).description();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).description();
        } else {
            throw SmallRyeMetricsMessages.msg.unknownMetricAnnotationType(annotation.annotationType());
        }
    }

    @Override
    public String displayName() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).displayName();
        } else if (annotation instanceof ConcurrentGauge) {
            return ((ConcurrentGauge) annotation).displayName();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).displayName();
        } else if (annotation instanceof Metered) {
            return ((Metered) annotation).displayName();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).displayName();
        } else if (annotation instanceof SimplyTimed) {
            return ((SimplyTimed) annotation).displayName();
        } else {
            throw SmallRyeMetricsMessages.msg.unknownMetricAnnotationType(annotation.annotationType());
        }
    }

    @Override
    public String annotationName() {
        return annotation.annotationType().getName();
    }

    @Override
    public String toString() {
        return annotation.toString();
    }
}
