package io.smallrye.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;

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

}
