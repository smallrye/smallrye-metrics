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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

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
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.SmallRyeMetricsLogging;
import io.smallrye.metrics.TagsUtils;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.interceptors.ConcurrentGaugeInterceptor;
import io.smallrye.metrics.interceptors.CountedInterceptor;
import io.smallrye.metrics.interceptors.GaugeRegistrationInterceptor;
import io.smallrye.metrics.interceptors.MeteredInterceptor;
import io.smallrye.metrics.interceptors.MetricName;
import io.smallrye.metrics.interceptors.MetricNameFactory;
import io.smallrye.metrics.interceptors.MetricResolver;
import io.smallrye.metrics.interceptors.MetricsBinding;
import io.smallrye.metrics.interceptors.SimplyTimedInterceptor;
import io.smallrye.metrics.interceptors.TimedInterceptor;

/**
 * @author hrupp
 */
public class MetricCdiInjectionExtension implements Extension {

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>() {
    };

    private static final AnnotationLiteral<Default> DEFAULT = new AnnotationLiteral<Default>() {
    };

    private final Map<Bean<?>, AnnotatedMember<?>> metricsFromProducers = new HashMap<>();

    private final Map<Bean<?>, List<AnnotatedMember<?>>> metricsFromAnnotatedMethods = new HashMap<>();

    private final List<Class<?>> metricsInterfaces;

    public MetricCdiInjectionExtension() {
        metricsInterfaces = new ArrayList<>();
    }

    private void addInterceptorBindings(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        SmallRyeMetricsLogging.log.logSmallRyeMetricsVersion(getImplementationVersion().orElse("unknown"));

        String extensionName = MetricCdiInjectionExtension.class.getName();

        // It seems that fraction deployment module cannot be picked up as a CDI bean archive - see also SWARM-1725
        for (Class clazz : new Class[] {
                MetricProducer.class,
                MetricNameFactory.class,
                GaugeRegistrationInterceptor.class,
                MetricRegistries.class,

                MeteredInterceptor.class,
                CountedInterceptor.class,
                ConcurrentGaugeInterceptor.class,
                TimedInterceptor.class,
                SimplyTimedInterceptor.class,
                MetricsRequestHandler.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
        }
    }

    // THORN-2068: MicroProfile Rest Client basic support
    private <X> void findAnnotatedInterfaces(@Observes @WithAnnotations({ Counted.class, Gauge.class, Metered.class,
            SimplyTimed.class, Timed.class, ConcurrentGauge.class }) ProcessAnnotatedType<X> pat) {
        Class<X> clazz = pat.getAnnotatedType().getJavaClass();
        Package pack = clazz.getPackage();
        if (pack != null && pack.getName().equals(GaugeRegistrationInterceptor.class.getPackage().getName())) {
            return;
        }
        if (clazz.isInterface()) {
            // All declared metrics of an annotated interface are registered during AfterDeploymentValidation
            metricsInterfaces.add(clazz);
        }
    }

    // for classes with at least one gauge, apply @MetricsBinding which serves for gauge registration
    private <X> void applyMetricsBinding(@Observes @WithAnnotations({ Gauge.class }) ProcessAnnotatedType<X> pat) {
        Class<X> clazz = pat.getAnnotatedType().getJavaClass();
        Package pack = clazz.getPackage();
        if (pack == null || !pack.getName().equals(GaugeRegistrationInterceptor.class.getPackage().getName())) {
            if (!clazz.isInterface()) {
                AnnotatedTypeDecorator newPAT = new AnnotatedTypeDecorator<>(pat.getAnnotatedType(), METRICS_BINDING);
                pat.setAnnotatedType(newPAT);
            }
        }

    }

    private <X> void findAnnotatedMethods(@Observes ProcessManagedBean<X> bean) {
        Package pack = bean.getBean().getBeanClass().getPackage();
        if (pack != null && pack.equals(GaugeRegistrationInterceptor.class.getPackage())) {
            return;
        }
        ArrayList<AnnotatedMember<?>> list = new ArrayList<>();
        for (AnnotatedMethod<? super X> aMethod : bean.getAnnotatedBeanClass().getMethods()) {
            Method method = aMethod.getJavaMember();
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                list.add(aMethod);
            }
        }
        list.addAll(bean.getAnnotatedBeanClass().getConstructors());
        if (!list.isEmpty()) {
            metricsFromAnnotatedMethods.put(bean.getBean(), list);
        }
    }

    private void findMetricProducerFields(@Observes ProcessProducerField<? extends Metric, ?> ppf) {
        SmallRyeMetricsLogging.log.producerFieldDiscovered(ppf.getAnnotatedProducerField());
        metricsFromProducers.put(ppf.getBean(), ppf.getAnnotatedProducerField());
    }

    private void findMetricProducerMethods(@Observes ProcessProducerMethod<? extends Metric, ?> ppm) {
        if (!ppm.getBean().getBeanClass().equals(MetricProducer.class)) {
            SmallRyeMetricsLogging.log.producerMethodDiscovered(ppm.getAnnotatedProducerMethod());
            metricsFromProducers.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
        }
    }

    void registerMetrics(@Observes AfterDeploymentValidation adv, BeanManager manager) {

        // Produce and register custom metrics
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        MetricName name = getReference(manager, MetricName.class);
        BeanInfoAdapter<Class<?>> beanInfoAdapter = new CDIBeanInfoAdapter();
        CDIMemberInfoAdapter memberInfoAdapter = new CDIMemberInfoAdapter();

        for (Map.Entry<Bean<?>, AnnotatedMember<?>> bean : metricsFromProducers.entrySet()) {
            if (// skip non @Default beans
            !bean.getKey().getQualifiers().contains(DEFAULT)
                    // skip producer methods with injection point metadata
                    || hasInjectionPointMetadata(bean.getValue())) {
                continue;
            }

            String metricName = name.of(bean.getValue());
            org.eclipse.microprofile.metrics.annotation.Metric metricAnnotation = bean.getValue()
                    .getAnnotation(org.eclipse.microprofile.metrics.annotation.Metric.class);
            if (metricAnnotation != null) {
                Object reference = getReference(manager, bean.getValue().getBaseType(), bean.getKey());
                if (reference == null) {
                    adv.addDeploymentProblem(new IllegalStateException("null was returned from " + bean.getValue()));
                    return;
                }
                Class<?> clazz = reference.getClass();
                MetricType type = MetricType.from(clazz.getInterfaces().length == 0 ? clazz.getSuperclass().getInterfaces()[0]
                        : clazz.getInterfaces()[0]);
                Metadata metadata = MetricsMetadata.getMetadata(metricAnnotation,
                        metricName,
                        metricAnnotation.unit(),
                        metricAnnotation.description(),
                        metricAnnotation.displayName(),
                        type);
                Tag[] tags = TagsUtils.parseTagsAsArray(metricAnnotation.tags());
                registry.register(metadata, getReference(manager, bean.getValue().getBaseType(), bean.getKey()), tags);
            } else {
                registry.register(metricName, getReference(manager, bean.getValue().getBaseType(), bean.getKey()));
            }
        }

        for (Map.Entry<Bean<?>, List<AnnotatedMember<?>>> entry : metricsFromAnnotatedMethods.entrySet()) {
            Bean<?> bean = entry.getKey();
            for (AnnotatedMember<?> method : entry.getValue()) {
                MetricsMetadata.registerMetrics(registry,
                        new MetricResolver(),
                        beanInfoAdapter.convert(bean.getBeanClass()),
                        memberInfoAdapter.convert(method.getJavaMember()));
            }
        }

        // THORN-2068: MicroProfile Rest Client basic support
        if (!metricsInterfaces.isEmpty()) {
            MetricResolver resolver = new MetricResolver();
            for (Class<?> metricsInterface : metricsInterfaces) {
                for (Method method : metricsInterface.getDeclaredMethods()) {
                    if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                        MetricsMetadata.registerMetrics(registry, resolver, beanInfoAdapter.convert(metricsInterface),
                                memberInfoAdapter.convert(method));
                    }
                }
            }
        }

        metricsInterfaces.clear();

        // Let's clear the collected metrics
        metricsFromProducers.clear();
        metricsFromAnnotatedMethods.clear();
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

    private Optional<String> getImplementationVersion() {
        return AccessController.doPrivileged(new PrivilegedAction<Optional<String>>() {
            @Override
            public Optional<String> run() {
                Properties properties = new Properties();
                try {
                    final InputStream resource = this.getClass().getClassLoader().getResourceAsStream("project.properties");
                    if (resource != null) {
                        properties.load(resource);
                        return Optional.ofNullable(properties.getProperty("smallrye.metrics.version"));
                    }
                } catch (IOException e) {
                    SmallRyeMetricsLogging.log.unableToDetectVersion();
                }
                return Optional.empty();
            }
        });
    }
}
