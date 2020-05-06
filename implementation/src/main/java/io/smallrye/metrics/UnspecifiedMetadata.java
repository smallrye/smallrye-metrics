/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * This is a special class to internally mark that no metadata was specified for a metric registration. We can't simply use null
 * instead of this because we still need to keep track of the metric name and type somewhere.
 * Instances of this class MUST NOT be actually stored in the MetricsRegistry, it needs to be converted to real metadata
 * first!!!
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
    public Optional<String> displayName() {
        throw new IllegalStateException("Unspecified metadata only contains name and type.");
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
    public String getType() {
        return type.toString();
    }

    @Override
    public MetricType getTypeRaw() {
        return type;
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
                .withType(type)
                .build();
    }
}
