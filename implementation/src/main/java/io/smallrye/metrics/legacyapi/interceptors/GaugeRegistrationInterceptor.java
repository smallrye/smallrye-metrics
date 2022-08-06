/*
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.interceptor.AroundConstruct;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.MemberInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.TagsUtils;
import io.smallrye.metrics.setup.MetricsMetadata;

@SuppressWarnings("unused")
@Interceptor
@MetricsBinding
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class GaugeRegistrationInterceptor {

    private MetricRegistry registry;

    private final MetricResolver resolver;

    GaugeRegistrationInterceptor() {
        this.resolver = new MetricResolver();
    }

    @AroundConstruct
    Object metrics(InvocationContext context) throws Exception {
        Class<?> type = context.getConstructor().getDeclaringClass();

        Object target = context.proceed();

        BeanInfoAdapter<Class<?>> beanInfoAdapter = new CDIBeanInfoAdapter();
        MemberInfoAdapter<Member> memberInfoAdapter = new CDIMemberInfoAdapter();

        // Registers the gauges over the bean type hierarchy after the target is constructed as it is required for the gauge invocations
        do {
            // TODO: discover annotations declared on implemented interfaces
            for (Method method : type.getDeclaredMethods()) {

                MetricResolver.Of<Gauge> gauge = resolver.gauge(beanInfoAdapter.convert(type),
                        memberInfoAdapter.convert(method));
                if (gauge.isPresent()) {
                    AnnotationInfo g = gauge.metricAnnotation();
                    Metadata metadata = MetricsMetadata.getMetadata(g, gauge.metricName(), g.unit(), g.description(),
                            g.displayName(), MetricType.GAUGE);

                    registry = SharedMetricRegistries.getOrCreate(g.scope());

                    /*
                     * Error/warning will be emitted by the runtime if return value
                     * is not a `Number`
                     */
                    registry.gauge(metadata,
                            context.getTarget(),
                            i -> (Number) invokeMethod(method, i),
                            TagsUtils.parseTagsAsArray(g.tags()));
                }
            }
            type = type.getSuperclass();
        } while (!Object.class.equals(type));

        return target;
    }

    private static Object invokeMethod(Method method, Object object) {
        try {
            return method.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException cause) {
            throw new IllegalStateException("Error while calling method [" + method + "]", cause);
        }
    }

}
