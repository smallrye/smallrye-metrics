package io.smallrye.metrics;

import java.util.Optional;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;

//XXX: Do we need this?
public class OriginAndMetadata implements Metadata {

    private final Object origin;
    private final Metadata metadata;

    public OriginAndMetadata(Object origin, Metadata metadata) {
        this.metadata = metadata;
        this.origin = origin;
    }

    public Object getOrigin() {
        return this.origin;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public String getDisplayName() {
        return metadata.getDisplayName();
    }

    @Override
    public Optional<String> displayName() {
        return metadata.displayName();
    }

    @Override
    public String getDescription() {
        return metadata.getDescription();
    }

    @Override
    public Optional<String> description() {
        return metadata.description();
    }

    @Override
    public String getType() {
        return metadata.getType();
    }

    @Override
    public MetricType getTypeRaw() {
        return metadata.getTypeRaw();
    }

    @Override
    public String getUnit() {
        return metadata.getUnit();
    }

    @Override
    public Optional<String> unit() {
        return metadata.unit();
    }

    @Override
    public String toString() {
        return metadata.toString();
    }

    @Override
    public boolean equals(Object o) {
        return metadata.equals(o);
    }

}
