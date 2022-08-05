package io.smallrye.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

@ApplicationScoped
public class MetricRegistryProducer {

    @Produces
    @Default
    public MetricRegistry getMetricRegistry(InjectionPoint ip) {

        RegistryType registryTypeAnnotation = ip.getAnnotated().getAnnotation(RegistryType.class);

        /*
         * Defaults to "application" scope if no scope parameter value is detected
         */
        if (registryTypeAnnotation == null) {
            return SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        } else {
            String annoScope = registryTypeAnnotation.scope();
            return SharedMetricRegistries.getOrCreate(annoScope);
        }
    }

}
