package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

import javax.enterprise.inject.Stereotype;

import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.BeanInfo;

public class CDIBeanInfo implements BeanInfo {

    private final Class<?> input;
    private final Package pkg;

    CDIBeanInfo(Class<?> input) {
        this.input = input;
        this.pkg = input.getPackage();
    }

    @Override
    public String getSimpleName() {
        return input.getSimpleName();
    }

    @Override
    public String getPackageName() {
        return pkg == null ? null : pkg.getName();
    }

    @Override
    public <T extends Annotation> AnnotationInfo getAnnotation(Class<T> metric) {
        T annotation = input.getAnnotation(metric);
        if (annotation != null) {
            return new CDIAnnotationInfoAdapter().convert(annotation);
        } else {
            // the metric annotation can also be applied via a stereotype, so look for stereotype annotations
            for (Annotation stereotypeCandidate : ((AnnotatedElement) input).getAnnotations()) {
                if (stereotypeCandidate.annotationType().isAnnotationPresent(Stereotype.class) &&
                        stereotypeCandidate.annotationType().isAnnotationPresent(metric)) {
                    return new CDIAnnotationInfoAdapter().convert(stereotypeCandidate.annotationType().getAnnotation(metric));
                }
            }
            return null;
        }
    }

    @Override
    public <T extends Annotation> boolean isAnnotationPresent(Class<T> metric) {
        if (input.isAnnotationPresent(metric)) {
            return true;
        } else {
            // the metric annotation can also be applied via a stereotype, so look for stereotype annotations
            for (Annotation stereotypeCandidate : ((AnnotatedElement) input).getAnnotations()) {
                if (stereotypeCandidate.annotationType().isAnnotationPresent(Stereotype.class) &&
                        stereotypeCandidate.annotationType().isAnnotationPresent(metric)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public BeanInfo getSuperclass() {
        Class<?> superclass = input.getSuperclass();
        if (superclass != null) {
            return new CDIBeanInfo(superclass);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return input.toString();
    }
}
