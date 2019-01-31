/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

import java.util.HashMap;
import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author hrupp
 */
public class ExtendedMetadata extends DefaultMetadata {

    private String mbean;
    boolean multi;
//    private List<Tag> tags;

    public ExtendedMetadata(String name, MetricType type) {
        this(name,null,null,type,null, null, false);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit) { // TODO
        this(name, displayName, description, typeRaw, unit, null, false);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit, String mbean, boolean multi) { // TODO
        super(name, displayName, description, typeRaw, unit, false);
        this.mbean = mbean;
        this.multi = multi;
    }

    public String getMbean() {
        return mbean;
    }

    public boolean isMulti() {
        return multi;
    }

/*    public List<Tag> getTags() {
        return tags;
    }*/

/*    public class Builder extends MetadataBuilder {

        private final Metadata metadata;

        private List<Tag> tags;

        private boolean multi;

        private String mbean;

        public Builder(Metadata metadata) {
            this.metadata = metadata;
        }

        public Builder mbean(String mbean) {
            this.mbean = mbean;
        }

        @Override
        public ExtendedMetadata build() {
            return new ExtendedMetadata(metadata.getName(),
                    metadata.getDisplayName(),
                    metadata.getDescription().orElse(null),
                    metadata.getTypeRaw(),
                    metadata.getUnit().orElse("none"),
                    tags);
        }


    }*/

}
