package io.smallrye.metrics;

import java.util.Optional;

import org.eclipse.microprofile.metrics.Metadata;

/*
 * Metadata object for metrics created through annotations or injection which
 * contains the "origin" (i.e. member or injection point)
 */
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
    public String getDescription() {
        return metadata.getDescription();
    }

    @Override
    public Optional<String> description() {
        return metadata.description();
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
