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
import java.lang.reflect.AnnotatedElement;

import javax.enterprise.inject.Stereotype;

import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.BeanInfo;

public class CDIBeanInfo implements BeanInfo {

    private final Class<?> input;

    CDIBeanInfo(Class<?> input) {
        this.input = input;
    }

    @Override
    public String getSimpleName() {
        return input.getSimpleName();
    }

    @Override
    public String getPackageName() {
        return input.getPackage().getName();
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
