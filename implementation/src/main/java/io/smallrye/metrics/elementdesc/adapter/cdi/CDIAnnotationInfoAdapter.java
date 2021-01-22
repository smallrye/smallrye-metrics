package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.annotation.Annotation;

import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.adapter.AnnotationInfoAdapter;

public class CDIAnnotationInfoAdapter implements AnnotationInfoAdapter<Annotation> {

    @Override
    public AnnotationInfo convert(Annotation annotation) {
        return new CDIAnnotationInfo(annotation);
    }

}
