/**
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 * <p>
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * <p>
 *   http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.smallrye.metrics.setup;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.inject.spi.Annotated;

/* package-private */ class AnnotatedDecorator implements Annotated {

    private final Annotated decorated;

    private final Set<Annotation> annotations;

    AnnotatedDecorator(Annotated decorated, Set<Annotation> annotations) {
        this.decorated = decorated;
        this.annotations = annotations;
    }

    @Override
    public Type getBaseType() {
        return decorated.getBaseType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        return decorated.getTypeClosure();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        T annotation = getDecoratingAnnotation(annotationType);
        if (annotation != null) {
            return annotation;
        } else {
            return decorated.getAnnotation(annotationType);
        }
    }

    @Override
    public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
        return decorated.getAnnotations(annotationType);
    }

    @Override
    public Set<Annotation> getAnnotations() {
        Set<Annotation> annotations = new HashSet<>(this.annotations);
        annotations.addAll(decorated.getAnnotations());
        return Collections.unmodifiableSet(annotations);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return getDecoratingAnnotation(annotationType) != null || decorated.isAnnotationPresent(annotationType);
    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation> T getDecoratingAnnotation(Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isAssignableFrom(annotation.annotationType())) {
                return (T) annotation;
            }
        }

        return null;
    }
}
