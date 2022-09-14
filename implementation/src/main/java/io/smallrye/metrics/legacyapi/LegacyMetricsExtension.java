package io.smallrye.metrics.legacyapi;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistryProducer;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsLogging;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.interceptors.CountedInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.GaugeRegistrationInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.MetricNameFactory;
import io.smallrye.metrics.legacyapi.interceptors.MetricResolver;
import io.smallrye.metrics.legacyapi.interceptors.MetricsBinding;
import io.smallrye.metrics.legacyapi.interceptors.TimedInterceptor;
import io.smallrye.metrics.micrometer.Backend;
import io.smallrye.metrics.micrometer.MicrometerBackends;
import io.smallrye.metrics.micrometer.RequiresClass;
import io.smallrye.metrics.setup.MetricsMetadata;

/**
 * CDI extension that provides functionality related to legacy MP Metrics 3.x API usage.
 */
public class LegacyMetricsExtension implements Extension {

    private final Map<Bean<?>, List<AnnotatedMember<?>>> metricsFromAnnotatedMethods = new HashMap<>();

    //CDI list of metricIDs - Is this really necessary, shutting down CDI means shutting down server.
    private final List<MetricID> metricIDs = new ArrayList<>();
    private final List<Class<?>> metricsInterfaces;

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>() {
    };

    public LegacyMetricsExtension() {
        metricsInterfaces = new ArrayList<>();
    }

    void logVersion(@Observes BeforeBeanDiscovery bbd) {
        SmallRyeMetricsLogging.log.logSmallRyeMetricsVersion(getImplementationVersion().orElse("unknown"));
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
                    //SmallRyeMetricsLogging.log.unableToDetectVersion();
                }
                return Optional.empty();
            }
        });
    }

    /**
     * Notifies CDI container to check for annotations. This is in place of beans.xml.
     */
    void registerAnnotatedTypes(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        String extensionName = LegacyMetricsExtension.class.getName();
        for (Class clazz : new Class[] {
                MetricProducer.class,
                MetricNameFactory.class,
                MetricRegistryProducer.class,
                MetricsRequestHandler.class,
                CountedInterceptor.class,
                GaugeRegistrationInterceptor.class,
                TimedInterceptor.class,
                MetricsRequestHandler.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
        }

        for (Class clazz : MicrometerBackends.classes()) {
            try {
                final RequiresClass requiresClass = (RequiresClass) clazz.getAnnotation(RequiresClass.class);
                final Class<?>[] requiredClass = requiresClass.value();
                bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
            } catch (Exception e) {
                // ignore and don't add
            }
        }
    }

    /*
     * For classes annotated with metrics (@Counted, etc, add to metricsInterface list - to address cdi injection).
     */
    private <X> void findAnnotatedInterfaces(
            @Observes @WithAnnotations({ Counted.class, Gauge.class, Timed.class }) ProcessAnnotatedType<X> pat) {
        Class<X> clazz = pat.getAnnotatedType().getJavaClass();
        Package pack = clazz.getPackage();

        //Guarding against adding classes in the "io.smallrye.metrics.legacyapi" from being processed
        if (pack != null && pack.getName().equals(GaugeRegistrationInterceptor.class.getPackage().getName())) {
            return;
        }
        if (clazz.isInterface()) {
            // All declared metrics of an annotated interface are registered during AfterDeploymentValidation
            metricsInterfaces.add(clazz);
        }
    }

    // ONLY FOR GUAGE: for classes with at least one gauge, apply @MetricsBinding which serves for gauge registration
    /*
     * For classes with @Gauge, decorate with @MetricsBinding
     */
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

    /*
     * Registers metrics for annotated methods and classes
     * Goes through every processed bean and adds their methods to the list
     * which will be handled by with MetricsMetadata.registerMetrics
     * Goes through each method and uses MetricResolver to see if the method is annotated itself or the class is
     * in which case a metric is created for it.
     */
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
        // create the "base" registry, this will allow the base metrics to be added
        // should this be done here, or should it be called by servers consuming this library?
        MetricRegistry baseRegistry = SharedMetricRegistries.getOrCreate(MetricRegistry.BASE_SCOPE);

        // register configured meter registries

        final Set<Bean<?>> beans = manager.getBeans(MeterRegistry.class, MicrometerBackends.class.getAnnotation(Backend.class));
        for (Bean<?> bean : beans) {
            final Object reference = manager.getReference(bean, MeterRegistry.class, manager.createCreationalContext(bean));
            if (MeterRegistry.class.isInstance(reference)) {
                Metrics.globalRegistry.add(MeterRegistry.class.cast(reference));
            }
        }

        // Produce and register custom metrics
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
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

    public void addMetricIds(List<MetricID> metricIDList) {
        //check for dups?
        metricIDs.addAll(metricIDList);
    }

    public void addMetricId(MetricID metricID) {
        //check for dups?
        metricIDs.add(metricID);
    }

    void unregisterMetrics(@Observes BeforeShutdown shutdown) {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        metricIDs.forEach(metricId -> registry.remove(metricId));
    }

}
