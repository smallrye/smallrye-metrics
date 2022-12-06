package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.annotation.Annotation;

import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Timed;

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
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).name();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).name();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }

    @Override
    public boolean absolute() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).absolute();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).absolute();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).absolute();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }

    @Override
    public String[] tags() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).tags();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).tags();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).tags();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }

    @Override
    public String unit() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).unit();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).unit();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).unit();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }

    @Override
    public String description() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).description();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).description();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).description();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
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

    @Override
    public String scope() {
        if (annotation instanceof Counted) {
            return ((Counted) annotation).scope();
        } else if (annotation instanceof Gauge) {
            return ((Gauge) annotation).scope();
        } else if (annotation instanceof Timed) {
            return ((Timed) annotation).scope();
        } else {
            throw new IllegalArgumentException("Unknown metric annotation type " + annotation.annotationType());
        }
    }
}
