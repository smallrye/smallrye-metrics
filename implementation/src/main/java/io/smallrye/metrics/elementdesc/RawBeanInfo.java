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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RawBeanInfo implements BeanInfo {

    private String simpleName;

    private String packageName;

    private List<AnnotationInfo> annotationInfos = new ArrayList<>();
    private AnnotationInfo[] infosArray;

    private BeanInfo superClassInfo;

    public RawBeanInfo() {

    }

    public RawBeanInfo(String simpleName, String packageName, Collection<AnnotationInfo> annotationInfos,
            BeanInfo superClassInfo) {
        this.simpleName = simpleName;
        this.packageName = packageName;
        this.annotationInfos.addAll(annotationInfos);
        this.superClassInfo = superClassInfo;
        this.infosArray = annotationInfos.toArray(new AnnotationInfo[] {});
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<AnnotationInfo> getAnnotationInfos() {
        return annotationInfos;
    }

    public void setAnnotationInfos(List<AnnotationInfo> annotationInfos) {
        this.annotationInfos = annotationInfos;
    }

    public AnnotationInfo[] getInfosArray() {
        return infosArray;
    }

    public void setInfosArray(AnnotationInfo[] infosArray) {
        this.infosArray = infosArray;
    }

    public BeanInfo getSuperClassInfo() {
        return superClassInfo;
    }

    public void setSuperClassInfo(BeanInfo superClassInfo) {
        this.superClassInfo = superClassInfo;
    }

    @Override
    public String getSimpleName() {
        return simpleName;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public <T extends Annotation> AnnotationInfo getAnnotation(Class<T> metric) {
        return annotationInfos.stream().filter(annotation -> annotation.annotationName().equals(metric.getName())).findFirst()
                .orElse(null);
    }

    @Override
    public <T extends Annotation> boolean isAnnotationPresent(Class<T> metric) {
        return annotationInfos.stream().anyMatch(annotation -> annotation.annotationName().equals(metric.getName()));
    }

    @Override
    public BeanInfo getSuperclass() {
        return superClassInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BeanInfo)) {
            return false;
        } else {
            return this.getSimpleName().equals(((BeanInfo) obj).getSimpleName());
        }
    }

    @Override
    public int hashCode() {
        return this.getSimpleName().hashCode();
    }
}
