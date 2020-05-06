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

}
