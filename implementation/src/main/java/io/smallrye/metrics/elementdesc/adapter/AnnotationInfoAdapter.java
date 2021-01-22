package io.smallrye.metrics.elementdesc.adapter;

import io.smallrye.metrics.elementdesc.AnnotationInfo;

public interface AnnotationInfoAdapter<I> {

    AnnotationInfo convert(I input);
}
