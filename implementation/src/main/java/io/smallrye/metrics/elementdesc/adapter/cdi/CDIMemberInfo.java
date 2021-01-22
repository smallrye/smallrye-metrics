package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.elementdesc.MemberType;

public class CDIMemberInfo implements MemberInfo {

    private final Object input;

    <T extends Member & AnnotatedElement> CDIMemberInfo(T input) {
        this.input = input;
    }

    @Override
    public MemberType getMemberType() {
        if (input instanceof Constructor) {
            return MemberType.CONSTRUCTOR;
        } else if (input instanceof Method) {
            return MemberType.METHOD;
        } else if (input instanceof Field) {
            return MemberType.FIELD;
        } else {
            throw new Error("Unknown/unsupported member type");
        }
    }

    @Override
    public String getDeclaringClassName() {
        return ((Member) input).getDeclaringClass().getName();
    }

    @Override
    public String getDeclaringClassSimpleName() {
        return ((Member) input).getDeclaringClass().getSimpleName();
    }

    @Override
    public String getName() {
        if (getMemberType().equals(MemberType.CONSTRUCTOR)) {
            return "<init>";
        } else {
            return ((Member) input).getName();
        }
    }

    @Override
    public <X extends Annotation> boolean isAnnotationPresent(Class<X> metric) {
        return ((AnnotatedElement) input).isAnnotationPresent(metric);
    }

    @Override
    public <X extends Annotation> AnnotationInfo getAnnotation(Class<X> metric) {
        X annotation = ((AnnotatedElement) input).getAnnotation(metric);
        if (annotation != null) {
            return new CDIAnnotationInfoAdapter().convert(annotation);
        } else {
            return null;
        }
    }

    @Override
    public String[] getParameterTypeNames() {
        if (input instanceof Constructor) {
            return Arrays.stream(((Constructor) input).getParameterTypes()).map(Class::getName).toArray(String[]::new);
        } else if (input instanceof Method) {
            return Arrays.stream(((Method) input).getParameterTypes()).map(Class::getName).toArray(String[]::new);
        } else {
            return new String[0];
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MemberInfo)) {
            return false;
        }
        MemberInfo other = (MemberInfo) obj;
        return other.getDeclaringClassName().equals(this.getDeclaringClassName()) &&
                other.getName().equals(this.getName()) &&
                Arrays.equals(other.getParameterTypeNames(), this.getParameterTypeNames());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getDeclaringClassName(), this.getName(), Arrays.hashCode(this.getParameterTypeNames()));
    }

    @Override
    public String toString() {
        return input.toString();
    }
}
