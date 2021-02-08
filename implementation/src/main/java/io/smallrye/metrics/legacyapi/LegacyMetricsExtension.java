package io.smallrye.metrics.legacyapi;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.interceptors.ConcurrentGaugeInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.CountedInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.GaugeRegistrationInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.MeteredInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.MetricNameFactory;
import io.smallrye.metrics.legacyapi.interceptors.MetricResolver;
import io.smallrye.metrics.legacyapi.interceptors.MetricsBinding;
import io.smallrye.metrics.legacyapi.interceptors.SimplyTimedInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.TimedInterceptor;
import io.smallrye.metrics.setup.MetricsMetadata;
import io.smallrye.metrics.setup.SmallRyeMetricsCdiExtension;

/**
 * CDI extension that provides functionality related to legacy MP Metrics 3.x API usage.
 */
public class LegacyMetricsExtension implements Extension {

    private final Map<Bean<?>, List<AnnotatedMember<?>>> metricsFromAnnotatedMethods = new HashMap<>();
    private final List<MetricID> metricIDs = new ArrayList<>();
    private final List<Class<?>> metricsInterfaces;

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>() {
    };

    public LegacyMetricsExtension() {
        metricsInterfaces = new ArrayList<>();
    }

    void registerAnnotatedTypes(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        String extensionName = SmallRyeMetricsCdiExtension.class.getName();
        for (Class clazz : new Class[] {
                MetricProducer.class,
                MetricNameFactory.class,
                MetricRegistries.class,
                MetricsRequestHandler.class,

                MeteredInterceptor.class,
                CountedInterceptor.class,
                ConcurrentGaugeInterceptor.class,
                GaugeRegistrationInterceptor.class,
                TimedInterceptor.class,
                SimplyTimedInterceptor.class,
                MetricsRequestHandler.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
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
        if (pack != null && pack.equals(CountedInterceptor.class.getPackage())) {
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

    void registerMetrics(@Observes AfterDeploymentValidation adv, BeanManager manager) {

        // Produce and register custom metrics
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        BeanInfoAdapter<Class<?>> beanInfoAdapter = new CDIBeanInfoAdapter();
        CDIMemberInfoAdapter memberInfoAdapter = new CDIMemberInfoAdapter();
        MetricResolver resolver = new MetricResolver();

        for (Map.Entry<Bean<?>, List<AnnotatedMember<?>>> entry : metricsFromAnnotatedMethods.entrySet()) {
            Bean<?> bean = entry.getKey();
            for (AnnotatedMember<?> method : entry.getValue()) {
                metricIDs.addAll(MetricsMetadata.registerMetrics(registry,
                        resolver,
                        beanInfoAdapter.convert(bean.getBeanClass()),
                        memberInfoAdapter.convert(method.getJavaMember())));
            }
        }

        // THORN-2068: MicroProfile Rest Client basic support
        if (!metricsInterfaces.isEmpty()) {
            for (Class<?> metricsInterface : metricsInterfaces) {
                for (Method method : metricsInterface.getDeclaredMethods()) {
                    if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                        metricIDs.addAll(MetricsMetadata.registerMetrics(registry,
                                resolver,
                                beanInfoAdapter.convert(metricsInterface),
                                memberInfoAdapter.convert(method)));
                    }
                }
            }
        }

        metricsInterfaces.clear();

        // Let's clear the collected metrics
        metricsFromAnnotatedMethods.clear();
    }

    void unregisterMetrics(@Observes BeforeShutdown shutdown) {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        metricIDs.forEach(metricId -> registry.remove(metricId));
    }

}
