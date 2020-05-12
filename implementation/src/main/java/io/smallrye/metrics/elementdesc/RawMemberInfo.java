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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class RawMemberInfo implements MemberInfo {

    private MemberType memberType;

    private String declaringClassName;

    private String declaringClassSimpleName;

    private String name;

    private String[] parameterTypeNames;

    private List<AnnotationInfo> annotationInfos = new ArrayList<>();

    public RawMemberInfo() {

    }

    public RawMemberInfo(MemberType memberType, String declaringClassName, String declaringClassSimpleName, String name,
            Collection<AnnotationInfo> annotationInfos) {
        this.memberType = memberType;
        this.declaringClassName = declaringClassName;
        this.declaringClassSimpleName = declaringClassSimpleName;
        this.name = name;
        this.annotationInfos.addAll(annotationInfos);
        this.parameterTypeNames = new String[0];
    }

    public RawMemberInfo(MemberType memberType, String declaringClassName, String declaringClassSimpleName, String name,
            Collection<AnnotationInfo> annotationInfos, String[] parameterTypes) {
        this.memberType = memberType;
        this.declaringClassName = declaringClassName;
        this.declaringClassSimpleName = declaringClassSimpleName;
        this.name = name;
        this.annotationInfos.addAll(annotationInfos);
        if (parameterTypes != null) {
            this.parameterTypeNames = parameterTypes;
        } else {
            this.parameterTypeNames = new String[0];
        }
    }

    public void setMemberType(MemberType memberType) {
        this.memberType = memberType;
    }

    public void setDeclaringClassName(String declaringClassName) {
        this.declaringClassName = declaringClassName;
    }

    public void setDeclaringClassSimpleName(String declaringClassSimpleName) {
        this.declaringClassSimpleName = declaringClassSimpleName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParameterTypeNames(String[] parameterTypeNames) {
        this.parameterTypeNames = parameterTypeNames;
    }

    public void setAnnotationInfos(List<AnnotationInfo> annotationInfos) {
        this.annotationInfos = annotationInfos;
    }

    public List<AnnotationInfo> getAnnotationInfos() {
        return annotationInfos;
    }

    @Override
    public MemberType getMemberType() {
        return memberType;
    }

    @Override
    public String getDeclaringClassName() {
        return declaringClassName;
    }

    @Override
    public String getDeclaringClassSimpleName() {
        return declaringClassSimpleName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <T extends Annotation> AnnotationInfo getAnnotation(Class<T> metricClass) {
        return annotationInfos.stream().filter(annotation -> annotation.annotationName().equals(metricClass.getName()))
                .findFirst().orElse(null);
    }

    @Override
    public String[] getParameterTypeNames() {
        return parameterTypeNames;
    }

    @Override
    public <T extends Annotation> boolean isAnnotationPresent(Class<T> metricClass) {
        return annotationInfos.stream().anyMatch(annotation -> annotation.annotationName().equals(metricClass.getName()));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MemberInfo)) {
            return false;
        }
        MemberInfo other = (MemberInfo) obj;
        return other.getDeclaringClassName().equals(this.getDeclaringClassName()) &&
                other.getName().equals(this.getName()) &&
                Arrays.equals(other.getParameterTypeNames(), this.parameterTypeNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.declaringClassName, this.name, Arrays.hashCode(this.parameterTypeNames));
    }

    @Override
    public String toString() {
        return "RawMemberInfo{" +
                "memberType=" + memberType +
                ", declaringClassName='" + declaringClassName + '\'' +
                ", name='" + name + '\'' +
                ", parameterTypeNames=" + Arrays.toString(parameterTypeNames) +
                '}';
    }
}