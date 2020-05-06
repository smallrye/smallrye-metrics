/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.smallrye.metrics;

import java.util.Optional;

import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * ExtendedMetadataBuilder.
 */
public class ExtendedMetadataBuilder extends MetadataBuilder {
    private String mbean;
    private boolean multi;
    private Boolean prependsScopeToOpenMetricsName;
    private boolean skipsScopeInOpenMetricsExportCompletely;
    private String openMetricsKeyOverride;

    public ExtendedMetadataBuilder() {
        super();
    }

    @Override
    public ExtendedMetadataBuilder withName(String name) {
        super.withName(name);
        return this;
    }

    @Override
    public ExtendedMetadataBuilder withDisplayName(String displayName) {
        super.withDisplayName(displayName);
        return this;
    }

    @Override
    public ExtendedMetadataBuilder withDescription(String description) {
        super.withDescription(description);
        return this;
    }

    @Override
    public ExtendedMetadataBuilder withType(MetricType type) {
        super.withType(type);
        return this;
    }

    @Override
    public ExtendedMetadataBuilder withUnit(String unit) {
        super.withUnit(unit);
        return this;
    }

    public ExtendedMetadataBuilder multi(final boolean multi) {
        this.multi = multi;
        return this;
    }

    public ExtendedMetadataBuilder withMbean(final String mbean) {
        this.mbean = mbean;
        return this;
    }

    public ExtendedMetadataBuilder prependsScopeToOpenMetricsName(final Boolean prependsScopeToOpenMetricsName) {
        this.prependsScopeToOpenMetricsName = prependsScopeToOpenMetricsName;
        return this;
    }

    public ExtendedMetadataBuilder skipsScopeInOpenMetricsExportCompletely(
            final boolean skipsScopeInOpenMetricsExportCompletely) {
        this.skipsScopeInOpenMetricsExportCompletely = skipsScopeInOpenMetricsExportCompletely;
        return this;
    }

    public ExtendedMetadataBuilder withOpenMetricsKeyOverride(final String openMetricsKeyOverride) {
        this.openMetricsKeyOverride = openMetricsKeyOverride;
        return this;
    }

    @Override
    public ExtendedMetadata build() {
        DefaultMetadata metadata = (DefaultMetadata) super.build();
        return new ExtendedMetadata(metadata.getName(),
                metadata.getDisplayName(),
                metadata.getDescription(),
                metadata.getTypeRaw(),
                metadata.getUnit(),
                this.mbean,
                this.multi,
                Optional.ofNullable(this.prependsScopeToOpenMetricsName),
                this.skipsScopeInOpenMetricsExportCompletely,
                this.openMetricsKeyOverride);
    }
}
