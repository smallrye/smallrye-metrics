package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@SuppressWarnings("unused")
@Timed
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class TimedInterceptor {

    private MetricRegistry registry;

    @AroundConstruct
    Object timedConstructor(InvocationContext context) throws Exception {
        return timedCallable(context, context.getConstructor());
    }

    @AroundInvoke
    Object timedMethod(InvocationContext context) throws Exception {
        return timedCallable(context, context.getMethod());
    }

    @AroundTimeout
    Object timedTimeout(InvocationContext context) throws Exception {
        return timedCallable(context, context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object timedCallable(InvocationContext invocationContext, E element)
            throws Exception {

        Timed timedAnno = element.getAnnotation(Timed.class);
        if (timedAnno != null)
            registry = SharedMetricRegistries.getOrCreate(timedAnno.scope());
        else
            registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);

        Set<MetricID> ids = ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings()
                .getTimers(new CDIMemberInfoAdapter<>().convert(element));

        if (ids == null || ids.isEmpty()) {
            throw new IllegalStateException("No metric mapped for " + element);
        }
        List<Timer.Context> contexts = ids.stream()
                .map(metricID -> {
                    Timer metric = registry.getTimers().get(metricID);
                    if (metric == null) {
                        throw new IllegalStateException(
                                "No Timer metric with ID " + metricID + " found in registry");
                    }
                    return metric;
                })
                .map(Timer::time)
                .collect(Collectors.toList());
        try {
            return invocationContext.proceed();
        } finally {
            for (Timer.Context timeContext : contexts) {
                timeContext.stop();
            }
        }
    }
}
