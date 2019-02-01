package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;

import java.util.Optional;

/**
 * This is a special class to internally mark that no metadata was specified for a metric registration. We can't simply use null
 * instead of this because we still need to keep track of the metric name and type somewhere.
 * Instances of this class MUST NOT be actually stored in the MetricsRegistry, it needs to be converted to real metadata first!!!
 */
public class UnspecifiedMetadata implements Metadata {

    private final String name;
    private final MetricType type;

    public UnspecifiedMetadata(String name, MetricType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public Optional<String> getDescription() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public String getType() {
        return type.toString();

    }

    @Override
    public MetricType getTypeRaw() {
        return type;
    }

    @Override
    public Optional<String> getUnit() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public boolean isReusable() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    public Metadata convertToRealMetadata() {
        return Metadata.builder()
                .withName(name)
                .withType(type)
                .build();
    }
}
