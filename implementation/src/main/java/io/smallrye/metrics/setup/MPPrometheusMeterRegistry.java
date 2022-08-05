package io.smallrye.metrics.setup;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Cheap way of setting "scope" information with the PrometheusMeterRegistry.
 * This is to allow the OpenMetricsExporter and JSON Exporter to scrape
 * the appropriate PrometheusMeterRegistry when using /metrics/scope/[metric]
 * query
 */
public class MPPrometheusMeterRegistry extends PrometheusMeterRegistry {

    private final String registryScope;

    public MPPrometheusMeterRegistry(PrometheusConfig config, String registryScope) {
        super(config);
        this.registryScope = registryScope;
    }

    public String getScope() {
        return registryScope;
    }

}
