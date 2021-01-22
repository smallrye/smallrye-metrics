package io.smallrye.metrics.exporters;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

// TODO: create Json exporter
public interface Exporter {

    String exportOneScope(MetricRegistry.Type scope);

    String exportAllScopes();

    String getContentType();

    /**
     * Exports just one metric obtained from a scope using its MetricID.
     */
    String exportOneMetric(MetricRegistry.Type scope, MetricID metricID);

    /**
     * Exports all metrics with the given name inside the given scope.
     */
    String exportMetricsByName(MetricRegistry.Type scope, String name);
}
