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

package io.smallrye.metrics.test.inject;

import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metric;

public class NonReusableMetricInjectionBean {

    @Counted(name = "mycounter", absolute = true, tags = { "k=v1" })
    public void counter() {
    }

    @Counted(name = "mycounter", absolute = true, tags = { "k=v2" })
    public void counter2() {
    }

    // each time this bean gets constructed and injected, change the values of metrics to verify that
    // we can inject metrics using an annotated method parameter
    @Inject
    public void increaseCountersOnInjecting(
            @Metric(name = "mycounter", absolute = true, tags = { "k=v1" }) Counter counter1,
            @Metric(name = "mycounter", absolute = true, tags = { "k=v2" }) Counter counter2) {
        counter1.inc(2);
        counter2.inc(3);
    }

}
