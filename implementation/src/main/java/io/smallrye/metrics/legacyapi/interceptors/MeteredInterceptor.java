package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Set;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Metered;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@SuppressWarnings("unused")
@Metered
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class MeteredInterceptor {

    private final MetricRegistry registry;

    @Inject
    MeteredInterceptor() {
        this.registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
    }

    @AroundConstruct
    Object meteredConstructor(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getConstructor());
    }

    @AroundInvoke
    Object meteredMethod(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getMethod());
    }

    @AroundTimeout
    Object meteredTimeout(InvocationContext context) throws Exception {
        return meteredCallable(context, context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object meteredCallable(InvocationContext context, E element)
            throws Exception {
        Set<MetricID> ids = ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings()
                .getMeters(new CDIMemberInfoAdapter<>().convert(element));
        if (ids == null || ids.isEmpty()) {
            throw SmallRyeMetricsMessages.msg.noMetricMappedForMember(element);
        }
        ids.stream()
                .map(metricID -> {
                    Meter metric = registry.getMeters().get(metricID);
                    if (metric == null) {
                        throw SmallRyeMetricsMessages.msg.noMetricFoundInRegistry(MetricType.METERED, metricID);
                    }
                    return metric;
                })
                .forEach(Meter::mark);
        return context.proceed();
    }
}
