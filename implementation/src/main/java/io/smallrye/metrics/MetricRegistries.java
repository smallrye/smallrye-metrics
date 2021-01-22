package io.smallrye.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

/**
 * @author hrupp
 */
@ApplicationScoped
public class MetricRegistries {

    @Produces
    @Default
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    @ApplicationScoped
    public MetricRegistry getApplicationRegistry() {
        return get(MetricRegistry.Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.BASE)
    @ApplicationScoped
    public MetricRegistry getBaseRegistry() {
        return get(MetricRegistry.Type.BASE);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    @ApplicationScoped
    public MetricRegistry getVendorRegistry() {
        return get(MetricRegistry.Type.VENDOR);
    }

    public static MetricRegistry get(MetricRegistry.Type type) {
        return registries.computeIfAbsent(type, t -> new LegacyMetricRegistryAdapter(type, Metrics.globalRegistry));
    }

    @PreDestroy
    public void cleanUp() {
        registries.remove(MetricRegistry.Type.APPLICATION);
    }

    /**
     * Drops a particular registry. If a reference to the same registry type
     * is requested later, a new empty registry will be created for that purpose.
     * 
     * @param type Type of registry that should be dropped.
     */
    public static void drop(MetricRegistry.Type type) {
        registries.remove(type);
    }

    /**
     * Drops all registries. If a reference to a registry
     * is requested later, a new empty registry will be created for that purpose.
     */
    public static void dropAll() {
        registries.remove(MetricRegistry.Type.APPLICATION);
        registries.remove(MetricRegistry.Type.BASE);
        registries.remove(MetricRegistry.Type.VENDOR);
    }

    private static final Map<MetricRegistry.Type, MetricRegistry> registries = new ConcurrentHashMap<>();

}
