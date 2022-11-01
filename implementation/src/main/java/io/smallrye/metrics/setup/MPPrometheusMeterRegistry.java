package io.smallrye.metrics.setup;

import org.eclipse.microprofile.config.ConfigProvider;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Cheap way of setting "scope" information with the PrometheusMeterRegistry. This is to allow the
 * OpenMetricsExporter and JSON Exporter to scrape the appropriate PrometheusMeterRegistry when
 * using /metrics/scope/[metric] query
 */
public class MPPrometheusMeterRegistry extends PrometheusMeterRegistry {

    private final String registryScope;

    private final static PrometheusConfig config = new PrometheusConfig() {

        @Override
        public String get(final String propertyName) {
            return ConfigProvider.getConfig().getOptionalValue("mp.metrics." + propertyName, String.class)
                    .orElse(null);
        }

    };

    public MPPrometheusMeterRegistry(PrometheusConfig config, String registryScope) {
        super(config);
        this.registryScope = registryScope;
    }

    public MPPrometheusMeterRegistry(String registryScope) {
        this(config, registryScope);
    }

    public String getScope() {
        return registryScope;
    }

}
