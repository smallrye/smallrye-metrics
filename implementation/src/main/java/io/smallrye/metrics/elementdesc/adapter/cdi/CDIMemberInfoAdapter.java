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

package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;

import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.elementdesc.adapter.MemberInfoAdapter;

public class CDIMemberInfoAdapter<T extends Member & AnnotatedElement> implements MemberInfoAdapter<T> {

    @Override
    public MemberInfo convert(T input) {
        return new CDIMemberInfo(input);
    }

}
