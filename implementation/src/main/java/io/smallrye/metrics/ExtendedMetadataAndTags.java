package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.Tag;

import java.util.List;

public class ExtendedMetadataAndTags {

    private final ExtendedMetadata metadata;

    private final List<Tag> tags;

    public ExtendedMetadataAndTags(ExtendedMetadata metadata, List<Tag> tags) {
        this.metadata = metadata;
        this.tags = tags;
    }

    public ExtendedMetadata getMetadata() {
        return metadata;
    }

    public List<Tag> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "ExtendedMetadataAndTags{" +
                "metadata=" + metadata +
                ", tags=" + tags +
                '}';
    }

}
