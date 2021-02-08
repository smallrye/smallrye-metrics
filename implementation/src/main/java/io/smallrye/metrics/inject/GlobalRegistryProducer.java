package io.smallrye.metrics.inject;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

@ApplicationScoped
public class GlobalRegistryProducer {

    @Produces
    MeterRegistry globalRegistry() {
        return Metrics.globalRegistry;
    }

}
