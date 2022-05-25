package io.smallrye.metrics.exporters;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

// TODO: create Json exporter
public interface Exporter {

    String exportOneScope(MetricRegistry.Type scope);

    String exportAllScopes();

    String getContentType();

    String exportOneMetric(MetricRegistry.Type scope, MetricID metricID);

    String exportMetricsByName(MetricRegistry.Type scope, String name);
}
