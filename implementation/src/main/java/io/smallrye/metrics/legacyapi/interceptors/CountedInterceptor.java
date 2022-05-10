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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@SuppressWarnings("unused")
@Counted
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class CountedInterceptor {

    private final MetricRegistry registry;

    @Inject
    CountedInterceptor() {
        this.registry = MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION);
    }

    @AroundConstruct
    Object countedConstructor(InvocationContext context) throws Exception {
        return countedCallable(context, context.getConstructor());
    }

    @AroundInvoke
    Object countedMethod(InvocationContext context) throws Exception {
        return countedCallable(context, context.getMethod());
    }

    @AroundTimeout
    Object countedTimeout(InvocationContext context) throws Exception {
        return countedCallable(context, context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object countedCallable(InvocationContext context, E element)
            throws Exception {
        Set<MetricID> ids = ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings()
                .getCounters(new CDIMemberInfoAdapter<>().convert(element));
        if (ids == null || ids.isEmpty()) {
            throw SmallRyeMetricsMessages.msg.noMetricMappedForMember(element);
        }
        ids.stream()
                .map(metricID -> {
                    Counter metric = registry.getCounters().get(metricID);
                    if (metric == null) {
                        throw SmallRyeMetricsMessages.msg.noMetricFoundInRegistry(MetricType.COUNTER, metricID);
                    }
                    return metric;
                })
                .forEach(Counter::inc);
        return context.proceed();
    }

}
