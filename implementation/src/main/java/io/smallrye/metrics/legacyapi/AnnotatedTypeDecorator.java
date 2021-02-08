package io.smallrye.metrics.legacyapi;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;

/* package-private */ final class AnnotatedTypeDecorator<X> extends AnnotatedDecorator implements AnnotatedType<X> {

    private final AnnotatedType<X> decoratedType;

    private final Set<AnnotatedMethod<? super X>> decoratedMethods;

    AnnotatedTypeDecorator(AnnotatedType<X> decoratedType, Annotation decoratingAnnotation) {
        this(decoratedType, decoratingAnnotation, Collections.<AnnotatedMethod<? super X>> emptySet());
    }

    AnnotatedTypeDecorator(AnnotatedType<X> decoratedType, Annotation decoratingAnnotation,
            Set<AnnotatedMethod<? super X>> decoratedMethods) {
        super(decoratedType, Collections.singleton(decoratingAnnotation));
        this.decoratedType = decoratedType;
        this.decoratedMethods = decoratedMethods;
    }

    @Override
    public Class<X> getJavaClass() {
        return decoratedType.getJavaClass();
    }

    @Override
    public Set<AnnotatedConstructor<X>> getConstructors() {
        return decoratedType.getConstructors();
    }

    @Override
    public Set<AnnotatedMethod<? super X>> getMethods() {
        Set<AnnotatedMethod<? super X>> methods = new HashSet<>(decoratedType.getMethods());
        for (AnnotatedMethod<? super X> method : decoratedMethods) {
            methods.remove(method);
            methods.add(method);
        }

        return Collections.unmodifiableSet(methods);
    }

    @Override
    public Set<AnnotatedField<? super X>> getFields() {
        return decoratedType.getFields();
    }
}
