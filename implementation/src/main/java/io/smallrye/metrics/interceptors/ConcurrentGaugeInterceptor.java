/*
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
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

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.jboss.logging.Logger;

import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;

@SuppressWarnings("unused")
@ConcurrentGauge
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class ConcurrentGaugeInterceptor {

    private static final Logger log = Logger.getLogger(ConcurrentGaugeInterceptor.class);

    private final Bean<?> bean;

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    ConcurrentGaugeInterceptor(@Intercepted Bean<?> bean, MetricRegistry registry) {
        this.bean = bean;
        this.registry = registry;
        this.resolver = new MetricResolver();
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
        BeanInfoAdapter<Class<?>> beanInfoAdapter = new CDIBeanInfoAdapter();
        CDIMemberInfoAdapter memberInfoAdapter = new CDIMemberInfoAdapter();
        MetricResolver.Of<ConcurrentGauge> concurrentGaugeResolver = resolver.concurrentGauge(
                bean != null ? beanInfoAdapter.convert(bean.getBeanClass())
                        : beanInfoAdapter.convert(element.getDeclaringClass()),
                memberInfoAdapter.convert(element));
        String name = concurrentGaugeResolver.metricName();
        Tag[] tags = concurrentGaugeResolver.tags();
        MetricID metricID = new MetricID(name, tags);
        org.eclipse.microprofile.metrics.ConcurrentGauge concurrentGauge = registry.getConcurrentGauges().get(metricID);
        if (concurrentGauge == null) {
            throw new IllegalStateException(
                    "No concurrent gauge with metricID [" + metricID + "] found in registry [" + registry + "]");
        }
        log.tracef("Increment concurrent gauge [metricId: %s]", metricID);
        concurrentGauge.inc();
        try {
            return context.proceed();
        } finally {
            log.tracef("Decrement concurrent gauge [metricID: %s]", metricID);
            concurrentGauge.dec();
        }
    }
}
