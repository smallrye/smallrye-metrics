package io.smallrye.metrics.elementdesc;

import java.lang.annotation.Annotation;

public interface MemberInfo {

    MemberType getMemberType();

    String getDeclaringClassName();

    String getDeclaringClassSimpleName();

    String getName();

    <T extends Annotation> boolean isAnnotationPresent(Class<T> metric);

    <T extends Annotation> AnnotationInfo getAnnotation(Class<T> metric);

    String[] getParameterTypeNames();

}
