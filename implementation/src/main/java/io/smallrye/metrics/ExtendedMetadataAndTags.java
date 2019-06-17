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

import java.util.List;

import org.eclipse.microprofile.metrics.Tag;

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
