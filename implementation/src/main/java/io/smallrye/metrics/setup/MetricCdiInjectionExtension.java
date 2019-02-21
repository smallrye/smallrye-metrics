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
package io.smallrye.metrics.setup;

import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.TagsUtils;
import io.smallrye.metrics.interceptors.ConcurrentGaugeInterceptor;
import io.smallrye.metrics.interceptors.CountedInterceptor;
import io.smallrye.metrics.interceptors.MeteredInterceptor;
import io.smallrye.metrics.interceptors.MetricName;
import io.smallrye.metrics.interceptors.MetricNameFactory;
import io.smallrye.metrics.interceptors.MetricResolver;
import io.smallrye.metrics.interceptors.MetricsBinding;
import io.smallrye.metrics.interceptors.MetricsInterceptor;
import io.smallrye.metrics.interceptors.TimedInterceptor;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.jboss.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hrupp
 */
public class MetricCdiInjectionExtension implements Extension {

    private static final Logger log = Logger.getLogger("io.smallrye.metrics");

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>() {
    };

    private static final AnnotationLiteral<Default> DEFAULT = new AnnotationLiteral<Default>() {
    };

    private final Map<Bean<?>, AnnotatedMember<?>> metrics = new HashMap<>();

    private final List<Class<?>> metricsInterfaces;

    public MetricCdiInjectionExtension() {
        log.debug("MetricCdiInjectionExtension");
        metricsInterfaces = new ArrayList<>();
    }

    private void addInterceptorBindings(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        log.info("MicroProfile: Metrics activated");

        String extensionName = MetricCdiInjectionExtension.class.getName();

        // It seems that fraction deployment module cannot be picked up as a CDI bean archive - see also SWARM-1725
        for (Class clazz : new Class[] {
                MetricProducer.class,
                MetricNameFactory.class,
                MetricsInterceptor.class,
                MetricRegistries.class,

                MeteredInterceptor.class,
                CountedInterceptor.class,
                ConcurrentGaugeInterceptor.class,
                TimedInterceptor.class,
                MetricsRequestHandler.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
        }
    }

    private <X> void metricsAnnotations(@Observes @WithAnnotations({ Counted.class, Gauge.class, Metered.class, Timed.class, ConcurrentGauge.class}) ProcessAnnotatedType<X> pat) {
        Class<X> clazz = pat.getAnnotatedType().getJavaClass();
        Package pack = clazz.getPackage();
        if (pack != null && pack.getName().equals(MetricsInterceptor.class.getPackage().getName())) {
            // Do not add MetricsBinding to metrics interceptor classes
            return;
        }
        if (clazz.isInterface()) {
            // THORN-2068: MicroProfile Rest Client basic support
            // All declared metrics of an annotated interface are registered during AfterDeploymentValidation
            metricsInterfaces.add(clazz);
        } else {
            AnnotatedTypeDecorator newPAT = new AnnotatedTypeDecorator<>(pat.getAnnotatedType(), METRICS_BINDING);
            log.debugf("annotations: %s", newPAT.getAnnotations());
            log.debugf("methods: %s", newPAT.getMethods());
            pat.setAnnotatedType(newPAT);
        }
    }

    private void metricProducerField(@Observes ProcessProducerField<? extends Metric, ?> ppf) {
        log.infof("Metrics producer field discovered: %s", ppf.getAnnotatedProducerField());
        metrics.put(ppf.getBean(), ppf.getAnnotatedProducerField());
    }

    private void metricProducerMethod(@Observes ProcessProducerMethod<? extends Metric, ?> ppm) {
        if (!ppm.getBean().getBeanClass().equals(MetricProducer.class)) {
            log.infof("Metrics producer method discovered: %s", ppm.getAnnotatedProducerMethod());
            metrics.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
        }
    }

    void registerMetrics(@Observes AfterDeploymentValidation adv, BeanManager manager) {

        // Produce and register custom metrics
        MetricRegistry registry = getReference(manager, MetricRegistry.class);
        MetricName name = getReference(manager, MetricName.class);
        for (Map.Entry<Bean<?>, AnnotatedMember<?>> bean : metrics.entrySet()) {
            if (// skip non @Default beans
            !bean.getKey().getQualifiers().contains(DEFAULT)
                    // skip producer methods with injection point metadata
                    || hasInjectionPointMetadata(bean.getValue())) {
                continue;
            }

            String metricName = name.of(bean.getValue());
            org.eclipse.microprofile.metrics.annotation.Metric metricAnnotation = bean.getValue().getAnnotation(org.eclipse.microprofile.metrics.annotation.Metric.class);
            if(metricAnnotation != null) {
                Object reference = getReference(manager, bean.getValue().getBaseType(), bean.getKey());
                Class<?> clazz = reference.getClass();
                MetricType type = MetricType.from(clazz.getInterfaces().length == 0 ? clazz.getSuperclass().getInterfaces()[0] : clazz.getInterfaces()[0]);
                Metadata metadata = MetricsMetadata.getMetadata(metricAnnotation,
                        metricName,
                        metricAnnotation.unit(),
                        metricAnnotation.description(),
                        metricAnnotation.displayName(),
                        type,
                        false);
                Tag[] tags = TagsUtils.parseTagsAsArray(metricAnnotation.tags());
                registry.register(metadata, getReference(manager, bean.getValue().getBaseType(), bean.getKey()), tags);
            } else {
                registry.register(metricName, getReference(manager, bean.getValue().getBaseType(), bean.getKey()));
            }
        }

        // THORN-2068: MicroProfile Rest Client basic support
        if (!metricsInterfaces.isEmpty()) {
            MetricResolver resolver = new MetricResolver();
            for (Class<?> metricsInterface : metricsInterfaces) {
                for (Method method : metricsInterface.getDeclaredMethods()) {
                    if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                        MetricsMetadata.registerMetrics(registry, resolver, metricsInterface, method);
                    }
                }
            }
        }
        metricsInterfaces.clear();

        // Let's clear the collected metric producers
        metrics.clear();
    }

    private static boolean hasInjectionPointMetadata(AnnotatedMember<?> member) {
        if (!(member instanceof AnnotatedMethod)) {
            return false;
        }
        AnnotatedMethod<?> method = (AnnotatedMethod<?>) member;
        for (AnnotatedParameter<?> parameter : method.getParameters()) {
            if (parameter.getBaseType().equals(InjectionPoint.class)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T getReference(BeanManager manager, Class<T> type) {
        return getReference(manager, type, manager.resolve(manager.getBeans(type)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T getReference(BeanManager manager, Type type, Bean<?> bean) {
        return (T) manager.getReference(bean, type, manager.createCreationalContext(bean));
    }
}
