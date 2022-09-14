package io.smallrye.metrics.legacyapi.interceptors;

import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;

@ApplicationScoped
public class MetricNameFactory {

    @Produces
    @ApplicationScoped
    MetricName metricName(BeanManager manager) {
        return new SeMetricName(Collections.emptySet()); // TODO
    }
}
