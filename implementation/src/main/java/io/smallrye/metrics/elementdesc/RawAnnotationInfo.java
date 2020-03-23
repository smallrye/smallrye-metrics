/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.elementdesc;

public class RawAnnotationInfo implements AnnotationInfo {

    private String name;

    private boolean absolute;

    private String[] tags;

    private String unit;

    private String description;

    private String displayName;

    private String annotationName;

    public RawAnnotationInfo() {

    }

    public RawAnnotationInfo(String name, boolean absolute, String[] tags, String unit,
            String description, String displayName, String annotationName) {
        this.name = name;
        this.absolute = absolute;
        this.tags = tags;
        this.unit = unit;
        this.description = description;
        this.displayName = displayName;
        this.annotationName = annotationName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public void setAbsolute(boolean absolute) {
        this.absolute = absolute;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public void setAnnotationName(String annotationName) {
        this.annotationName = annotationName;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean absolute() {
        return absolute;
    }

    @Override
    public String[] tags() {
        return tags;
    }

    @Override
    public String unit() {
        return unit;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String annotationName() {
        return annotationName;
    }

}