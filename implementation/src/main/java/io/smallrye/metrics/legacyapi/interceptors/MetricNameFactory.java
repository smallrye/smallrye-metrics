package io.smallrye.metrics.legacyapi.interceptors;

import java.util.Collections;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;

@ApplicationScoped
public class MetricNameFactory {

    @Produces
    @ApplicationScoped
    MetricName metricName(BeanManager manager) {
        return new SeMetricName(Collections.emptySet()); // TODO
    }
}
