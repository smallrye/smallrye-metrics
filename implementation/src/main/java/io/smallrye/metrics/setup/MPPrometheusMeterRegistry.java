package io.smallrye.metrics.setup;

import org.eclipse.microprofile.metrics.MetricRegistry;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Cheap way of setting "scope" information with the PrometheusMeterRegistry.
 * This is to allow the OpenMetricsExporter and JSON Exporter to scrape
 * the appropriate PrometheusMeterRegistry when using /metrics/<scope/[<metric>]
 * query
 */
public class MPPrometheusMeterRegistry extends PrometheusMeterRegistry {

    private final MetricRegistry.Type registryType;

    public MPPrometheusMeterRegistry(PrometheusConfig config, MetricRegistry.Type registryType) {
        super(config);
        this.registryType = registryType;
    }

    public MetricRegistry.Type getType() {
        return registryType;
    }

}
