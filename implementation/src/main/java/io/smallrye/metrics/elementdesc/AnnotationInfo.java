package io.smallrye.metrics.elementdesc;

public interface AnnotationInfo {

    String name();

    boolean absolute();

    String[] tags();

    String unit();

    String description();

    String annotationName();

    String scope();

}