/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package io.smallrye.metrics.interceptors;

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
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRegistryImpl;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;

@SuppressWarnings("unused")
@Timed
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class TimedInterceptor {

    private final MetricRegistry registry;

    @Inject
    TimedInterceptor() {
        this.registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
    }

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
        Set<MetricID> ids = ((MetricsRegistryImpl) registry).getMemberToMetricMappings()
                .getTimers(new CDIMemberInfoAdapter<>().convert(element));

        if (ids == null || ids.isEmpty()) {
            throw new IllegalStateException("No metric mapped for " + element);
        }
        List<Timer.Context> contexts = ids.stream()
                .map(metricID -> {
                    Timer metric = registry.getTimers().get(metricID);
                    if (metric == null) {
                        throw new IllegalStateException(
                                "No timer with metricID [" + metricID + "] found in registry [" + registry + "]");
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
