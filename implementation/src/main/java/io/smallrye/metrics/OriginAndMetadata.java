package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;

import java.util.Optional;

/**
 * Created by bob on 2/5/18.
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
    public String getDisplayName() {
        return metadata.getDisplayName();

    }

    @Override
    public Optional<String> getDescription() {
        return metadata.getDescription();

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
    public Optional<String> getUnit() {
        return metadata.getUnit();

    }

    @Override
    public boolean isReusable() {
        return metadata.isReusable();

    }
}
