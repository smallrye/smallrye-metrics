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

import javax.annotation.Priority;
import javax.enterprise.inject.Intercepted;
import javax.enterprise.inject.spi.Bean;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.Metered;

import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;

@SuppressWarnings("unused")
@Metered
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class MeteredInterceptor {

    private final Bean<?> bean;

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    MeteredInterceptor(@Intercepted Bean<?> bean, MetricRegistry registry) {
        this.bean = bean;
        this.registry = registry;
        this.resolver = new MetricResolver();
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
        BeanInfoAdapter<Class<?>> beanInfoAdapter = new CDIBeanInfoAdapter();
        CDIMemberInfoAdapter memberInfoAdapter = new CDIMemberInfoAdapter();
        MetricResolver.Of<Metered> meterResolver = resolver.metered(
                bean != null ? beanInfoAdapter.convert(bean.getBeanClass())
                        : beanInfoAdapter.convert(element.getDeclaringClass()),
                memberInfoAdapter.convert(element));
        String name = meterResolver.metricName();
        Tag[] tags = meterResolver.tags();
        MetricID metricID = new MetricID(name, tags);
        Meter meter = (Meter) registry.getMetrics().get(metricID);
        if (meter == null) {
            throw new IllegalStateException("No meter with metricID [" + metricID + "] found in registry [" + registry + "]");
        }

        meter.mark();
        return context.proceed();
    }
}
