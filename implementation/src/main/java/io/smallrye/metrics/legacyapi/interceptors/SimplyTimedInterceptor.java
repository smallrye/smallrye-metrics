package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@SuppressWarnings("unused")
@SimplyTimed
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class SimplyTimedInterceptor {

    private final MetricRegistry registry;

    @Inject
    SimplyTimedInterceptor() {
        this.registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
    }

    @AroundConstruct
    Object simplyTimedConstructor(InvocationContext context) throws Exception {
        return timedCallable(context, context.getConstructor());
    }

    @AroundInvoke
    Object simplyTimedMethod(InvocationContext context) throws Exception {
        return timedCallable(context, context.getMethod());
    }

    @AroundTimeout
    Object simplyTimedTimeout(InvocationContext context) throws Exception {
        return timedCallable(context, context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object timedCallable(InvocationContext invocationContext, E element)
            throws Exception {
        Set<MetricID> ids = ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings()
                .getSimpleTimers(new CDIMemberInfoAdapter<>().convert(element));
        if (ids == null || ids.isEmpty()) {
            throw SmallRyeMetricsMessages.msg.noMetricMappedForMember(element);
        }
        List<SimpleTimer.Context> contexts = ids.stream()
                .map(metricID -> {
                    SimpleTimer metric = registry.getSimpleTimers().get(metricID);
                    if (metric == null) {
                        throw SmallRyeMetricsMessages.msg.noMetricFoundInRegistry(MetricType.SIMPLE_TIMER, metricID);
                    }
                    return metric;
                })
                .map(SimpleTimer::time)
                .collect(Collectors.toList());
        try {
            return invocationContext.proceed();
        } finally {
            for (SimpleTimer.Context timeContext : contexts) {
                timeContext.stop();
            }
        }
    }
}
