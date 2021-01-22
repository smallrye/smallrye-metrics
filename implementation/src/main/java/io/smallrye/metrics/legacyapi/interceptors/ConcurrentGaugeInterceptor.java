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
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@SuppressWarnings("unused")
@ConcurrentGauge
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class ConcurrentGaugeInterceptor {

    private final MetricRegistry registry;

    @Inject
    ConcurrentGaugeInterceptor() {
        this.registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
    }

    @AroundConstruct
    Object countedConstructor(InvocationContext context) throws Exception {
        return concurrentCallable(context, context.getConstructor());
    }

    @AroundInvoke
    Object countedMethod(InvocationContext context) throws Exception {
        return concurrentCallable(context, context.getMethod());
    }

    @AroundTimeout
    Object countedTimeout(InvocationContext context) throws Exception {
        return concurrentCallable(context, context.getMethod());
    }

    private <E extends Member & AnnotatedElement> Object concurrentCallable(InvocationContext context, E element)
            throws Exception {
        Set<MetricID> ids = ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings()
                .getConcurrentGauges(new CDIMemberInfoAdapter<>().convert(element));
        if (ids == null || ids.isEmpty()) {
            throw SmallRyeMetricsMessages.msg.noMetricMappedForMember(element);
        }
        List<org.eclipse.microprofile.metrics.ConcurrentGauge> metrics = ids
                .stream()
                .map(metricID -> {
                    org.eclipse.microprofile.metrics.ConcurrentGauge metric = registry.getConcurrentGauges().get(metricID);
                    if (metric == null) {
                        throw SmallRyeMetricsMessages.msg.noMetricFoundInRegistry(MetricType.CONCURRENT_GAUGE, metricID);
                    }
                    return metric;
                })
                .collect(Collectors.toList());
        for (org.eclipse.microprofile.metrics.ConcurrentGauge metric : metrics) {
            metric.inc();
        }
        try {
            return context.proceed();
        } finally {
            for (org.eclipse.microprofile.metrics.ConcurrentGauge metric : metrics) {
                metric.dec();
            }
        }
    }
}
