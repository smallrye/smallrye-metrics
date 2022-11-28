package io.smallrye.metrics.legacyapi;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.annotation.Metric;

// TODO: This was taken from the Quarkus extension and maybe we could do without it?
class MpMetadata implements Metadata {

    //sanitize? just removes the display name (shares it with the metric name intead)
    public static MpMetadata sanitize(Metadata metadata) {
        if (metadata instanceof MpMetadata) {
            return (MpMetadata) metadata;
        }
        return new MpMetadata(metadata);
    }

    final String name;
    String description;
    String unit;
    boolean dirty = false;

    MpMetadata(String name) {
        this.name = name;
    }

    MpMetadata(Metric annotation) {
        this.name = annotation.name();
        this.description = stringOrNull(annotation.description());
        this.unit = stringOrNull(annotation.unit());
    }

    MpMetadata(String name, String description, String unit) {
        this.name = name;
        this.description = stringOrNull(description);
        this.unit = stringOrNull(unit);
    }

    MpMetadata(Metadata other) {
        this.name = other.getName();
        this.description = other.description().orElse(null);
        this.unit = other.unit().orElse(null);
    }

    public boolean mergeSameType(Metadata metadata) {
        if (description == null) {
            dirty = true;
            description = stringOrNull(metadata.description().orElse(null));
        }
        if (unit == null) {
            dirty = true;
            unit = stringOrNull(metadata.unit().orElse(null));
        }
        return true;
    }

    public MpMetadata merge(Metric annotation) {
        if (description == null) {
            dirty = true;
            description = stringOrNull(annotation.description());
        }
        if (unit == null) {
            dirty = true;
            unit = stringOrNull(annotation.unit());
        }
        return this;
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @Override
    public Optional<String> unit() {
        return Optional.ofNullable(unit);
    }

    public boolean cleanDirtyMetadata() {
        boolean precheck = dirty;
        dirty = false;
        return precheck;
    }

    String stringOrNull(String s) {
        if (s == null || s.isEmpty() || "none".equals(s))
            return null;
        return s;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MpMetadata that = (MpMetadata) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(unit, that.unit);
    }

    /*
     * Temporary work around due to https://github.com/eclipse/microprofile-metrics/issues/760
     */
    public boolean equalsTimers(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MpMetadata that = (MpMetadata) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name
                + "["
                + (description == null ? "" : "description=" + description + " ")
                + (unit == null ? "" : "unit=" + unit + " ")
                + "]";
    }
}
