package io.smallrye.metrics.elementdesc;

import java.lang.annotation.Annotation;

public interface BeanInfo {

    String getSimpleName();

    String getPackageName();

    <T extends Annotation> AnnotationInfo getAnnotation(Class<T> metric);

    <T extends Annotation> boolean isAnnotationPresent(Class<T> metric);

    BeanInfo getSuperclass();
}
