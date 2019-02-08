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

import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * @author hrupp
 */
public class ExtendedMetadata extends DefaultMetadata {

    private String mbean;
    boolean multi;

    public ExtendedMetadata(String name, MetricType type) {
        this(name,null,null,type,null, null, false);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit) {
        this(name, displayName, description, typeRaw, unit, null, false);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit, String mbean, boolean multi) {
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

}
