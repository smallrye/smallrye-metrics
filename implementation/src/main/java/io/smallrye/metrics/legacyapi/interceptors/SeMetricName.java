package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Metric;

@Vetoed
public class SeMetricName implements MetricName {

    private final Set<MetricsParameter> parameters;

    public SeMetricName(Set<MetricsParameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String of(InjectionPoint ip) {
        Annotated annotated = ip.getAnnotated();
        if (annotated instanceof AnnotatedMember) {
            return of((AnnotatedMember<?>) annotated);
        } else if (annotated instanceof AnnotatedParameter) {
            return of((AnnotatedParameter<?>) annotated);
        } else {
            throw new UnsupportedOperationException("Unable to retrieve metric name for injection point " + ip);
        }
    }

    @Override
    public String of(AnnotatedMember<?> member) {
        if (member.isAnnotationPresent(Metric.class)) {
            Metric metric = member.getAnnotation(Metric.class);
            String name = (metric.name().isEmpty()) ? member.getJavaMember().getName() : of(metric.name());
            return metric.absolute() | parameters.contains(MetricsParameter.useAbsoluteName) ? name
                    : MetricRegistry.name(member.getJavaMember().getDeclaringClass(), name);
        } else {
            return parameters.contains(MetricsParameter.useAbsoluteName) ? member.getJavaMember().getName()
                    : MetricRegistry.name(member.getJavaMember().getDeclaringClass(), member.getJavaMember().getName());
        }
    }

    @Override
    public String of(String attribute) {
        return attribute;
    }

    private String of(AnnotatedParameter<?> parameter) {
        if (parameter.isAnnotationPresent(Metric.class)) {
            Metric metric = parameter.getAnnotation(Metric.class);
            String name = (metric.name().isEmpty()) ? getParameterName(parameter) : of(metric.name());
            return metric.absolute() | parameters.contains(MetricsParameter.useAbsoluteName) ? name
                    : MetricRegistry.name(parameter.getDeclaringCallable().getJavaMember().getDeclaringClass(), name);
        } else {
            return parameters.contains(MetricsParameter.useAbsoluteName) ? getParameterName(parameter)
                    : MetricRegistry.name(parameter.getDeclaringCallable().getJavaMember().getDeclaringClass(),
                            getParameterName(parameter));
        }
    }

    // Let's rely on reflection to retrieve the parameter name until Java 8 is required.
    // To be refactored eventually when CDI SPI integrate JEP-118.
    // See http://openjdk.java.net/jeps/118
    // And http://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html
    // TODO: move into a separate metric name strategy
    private String getParameterName(AnnotatedParameter<?> parameter) {
        try {
            Method method = Method.class.getMethod("getParameters");
            Object[] parameters = (Object[]) method.invoke(parameter.getDeclaringCallable().getJavaMember());
            Object param = parameters[parameter.getPosition()];
            Class<?> Parameter = Class.forName("java.lang.reflect.Parameter");
            if ((Boolean) Parameter.getMethod("isNamePresent").invoke(param)) {
                return (String) Parameter.getMethod("getName").invoke(param);
            } else {
                throw new UnsupportedOperationException("Unable to retrieve metric name for injection point " + parameter);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException cause) {
            throw new UnsupportedOperationException("Unable to retrieve metric name for injection point " + parameter);
        }
    }
}
