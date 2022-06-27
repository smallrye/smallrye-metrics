package io.smallrye.metrics.exporters;

import org.eclipse.microprofile.metrics.MetricID;

// TODO: create Json exporter
public interface Exporter {

    String exportOneScope(String scope);

    String exportAllScopes();

    String getContentType();

    String exportOneMetric(String scope, MetricID metricID);

    String exportMetricsByName(String scope, String name);
}
