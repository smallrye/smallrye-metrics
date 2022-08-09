package io.smallrye.metrics.exporters;

public interface Exporter {

    String exportOneScope(String scope);

    String exportAllScopes();

    String getContentType();

    String exportOneMetricAcrossScopes(String name);

    String exportMetricsByName(String scope, String name);
}
