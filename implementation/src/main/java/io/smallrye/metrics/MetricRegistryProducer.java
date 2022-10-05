package io.smallrye.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

@ApplicationScoped
public class MetricRegistryProducer {

    @Produces
    @Default
    public MetricRegistry getMetricRegistry(InjectionPoint ip) {

        RegistryScope registryScopeAnnotation = ip.getAnnotated().getAnnotation(RegistryScope.class);

        /*
         * Defaults to "application" scope if no scope parameter value is detected
         */
        if (registryScopeAnnotation == null) {
            return SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        } else {
            String annoScope = registryScopeAnnotation.scope();
            return SharedMetricRegistries.getOrCreate(annoScope);
        }
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    public MetricRegistry getApplicationRegistry() {
        return SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.BASE)
    public MetricRegistry getBaseRegistry() {
        return SharedMetricRegistries.getOrCreate(MetricRegistry.BASE_SCOPE);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    public MetricRegistry getVendorRegistry() {
        return SharedMetricRegistries.getOrCreate(MetricRegistry.VENDOR_SCOPE);
    }

}
