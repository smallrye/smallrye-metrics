package io.smallrye.metrics;

import org.eclipse.microprofile.config.ConfigProvider;

import io.micrometer.prometheus.PrometheusConfig;

/**
 *
 * MPPrometheusConfig is an implementation of the {@link PrometheusConfig} which will accept Prometheus
 * related configuration values prepended with "mp.metrics.". This Config is used for the
 * {@link io.micrometer.prometheus.PrometheusMeterRegistry}
 * that is created for the MicroProfile Metric Registries in {@link SharedMetricRegistries}. This Config is not created within
 * the
 * {@link SharedMetricRegistries} due to some vendors having to load the SmallRye classes with reflection and the possibility of
 * the
 * Micrometer Prometheus library not being on the class path during runtime.
 *
 */
public class MPPrometheusConfig implements PrometheusConfig {

    @Override
    public String get(final String propertyName) {
        return ConfigProvider.getConfig().getOptionalValue("mp.metrics." + propertyName, String.class).orElse(null);
    }
}
