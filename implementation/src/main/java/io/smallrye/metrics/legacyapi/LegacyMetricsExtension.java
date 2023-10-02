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
import java.util.logging.Level;
import java.util.logging.Logger;

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

import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistryProducer;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIBeanInfoAdapter;
import io.smallrye.metrics.elementdesc.adapter.cdi.CDIMemberInfoAdapter;
import io.smallrye.metrics.legacyapi.interceptors.CountedInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.GaugeRegistrationInterceptor;
import io.smallrye.metrics.legacyapi.interceptors.MetricNameFactory;
import io.smallrye.metrics.legacyapi.interceptors.MetricResolver;
import io.smallrye.metrics.legacyapi.interceptors.MetricsBinding;
import io.smallrye.metrics.legacyapi.interceptors.TimedInterceptor;
import io.smallrye.metrics.setup.MetricsMetadata;

/**
 * CDI extension that provides functionality related to legacy MP Metrics 3.x API usage.
 */
public class LegacyMetricsExtension implements Extension {

    private static final String CLASS_NAME = LegacyMetricsExtension.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private final Map<Bean<?>, List<AnnotatedMember<?>>> metricsFromAnnotatedMethods = new HashMap<>();

    //CDI list of metricIDs - Is this really necessary, shutting down CDI means shutting down server.
    private final List<MetricID> metricIDs = new ArrayList<>();
    private final List<Class<?>> metricsInterfaces;

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>() {
    };

    public LegacyMetricsExtension() {
        metricsInterfaces = new ArrayList<>();
    }

    public void logVersion(@Observes BeforeBeanDiscovery bbd) {
        final String METHOD_NAME = "logVersion";
        LOGGER.logp(Level.INFO, CLASS_NAME, METHOD_NAME, "MicroProfile: Metrics activated (SmallRye Metrics version: {0})",
                getImplementationVersion().orElse("unknown"));
    }

    private Optional<String> getImplementationVersion() {
        final String METHOD_NAME = "getImplementationVersion";
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
                    //Unable to detect version of SmallRye Metrics
                    LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "Unable to detect version of SmallRye Metrics");
                }
                return Optional.empty();
            }
        });
    }

    /**
     * Notifies CDI container to check for annotations. This is in place of beans.xml.
     *
     * @param bbd the {@link BeforeBeanDiscovery}
     * @param manager the {@link BeanManager}
     */
    public void registerAnnotatedTypes(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        String extensionName = LegacyMetricsExtension.class.getName();
        for (Class clazz : new Class[] {
                MetricProducer.class,
                MetricNameFactory.class,
                MetricRegistryProducer.class,
                MetricsRequestHandler.class,
                CountedInterceptor.class,
                GaugeRegistrationInterceptor.class,
                TimedInterceptor.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
        }
    }

    /**
     * Used for proxy implementations of this extension class where the proxy
     * is registered as an extension to the CDI runtime.
     * <br>
     * Depending on the class loader structure of the proxy class and this class, WELD
     * will not register the producers appropriately. However, interceptors are registered properly.
     * Additionally, MetricProducer injects LegacyMetricsExtension which
     * can not be resolved if this extension class is proxied.
     *
     * @param bbd the {@link BeforeBeanDiscovery}
     * @param manager the {@link BeanManager}
     */
    public void registerAnnotatedTypesProxy(BeforeBeanDiscovery bbd, BeanManager manager) {
        String extensionName = LegacyMetricsExtension.class.getName();
        for (Class clazz : new Class[] {
                MetricsRequestHandler.class,
                CountedInterceptor.class,
                GaugeRegistrationInterceptor.class,
                TimedInterceptor.class
        }) {
            bbd.addAnnotatedType(manager.createAnnotatedType(clazz), extensionName + "_" + clazz.getName());
        }
    }

    /*
     * For classes annotated with metrics (@Counted, etc, add to metricsInterface list - to address cdi injection).
     */
    public <X> void findAnnotatedInterfaces(
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
    public <X> void applyMetricsBinding(@Observes @WithAnnotations({ Gauge.class }) ProcessAnnotatedType<X> pat) {
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
    public <X> void findAnnotatedMethods(@Observes ProcessManagedBean<X> bean) {
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

    public void registerMetrics(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        // create the "base" registry, this will allow the base metrics to be added
        // should this be done here, or should it be called by servers consuming this library?
        MetricRegistry baseRegistry = SharedMetricRegistries.getOrCreate(MetricRegistry.BASE_SCOPE);

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
        metricIDs.addAll(metricIDList);
    }

    public void addMetricId(MetricID metricID) {
        metricIDs.add(metricID);
    }

    void unregisterMetrics(@Observes BeforeShutdown shutdown) {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        metricIDs.forEach(metricId -> registry.remove(metricId));
    }

}
