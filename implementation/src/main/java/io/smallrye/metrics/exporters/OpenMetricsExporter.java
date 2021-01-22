package io.smallrye.metrics.exporters;

import java.util.Optional;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class OpenMetricsExporter implements Exporter {

    private final PrometheusMeterRegistry registry;

    public OpenMetricsExporter() {
        // FIXME: this only allows one prometheus registry; should we potentially allow more?
        Optional<MeterRegistry> prometheusRegistry = Metrics.globalRegistry.getRegistries().stream()
                .filter(registry -> registry instanceof PrometheusMeterRegistry)
                .findFirst();
        if (prometheusRegistry.isPresent()) {
            this.registry = (PrometheusMeterRegistry) prometheusRegistry.get();
        } else {
            throw new IllegalStateException("Prometheus registry was not found in the global registry");
        }
    }

    @Override
    public String exportAllScopes() {
        return registry.scrape();
    }

    @Override
    public String exportOneScope(MetricRegistry.Type scope) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String exportMetricsByName(MetricRegistry.Type scope, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

}
