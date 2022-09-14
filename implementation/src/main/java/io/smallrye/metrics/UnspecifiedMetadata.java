package io.smallrye.metrics;

import java.util.Optional;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * This is a special class to internally mark that no metadata was specified for a metric registration. We can't simply use null
 * instead of this because we still need to keep track of the metric name and type somewhere.
 * Instances of this class MUST NOT be actually stored in the MetricsRegistry, it needs to be converted to real metadata
 * first!!!
 */
public class UnspecifiedMetadata implements Metadata {

    private final String name;

    public UnspecifiedMetadata(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public Optional<String> description() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public String getUnit() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    @Override
    public Optional<String> unit() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
    }

    public Metadata convertToRealMetadata() {
        return Metadata.builder()
                .withName(name)
                .build();
    }
}
