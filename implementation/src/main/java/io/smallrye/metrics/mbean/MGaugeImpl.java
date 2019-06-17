/*
 *
 *   Copyright 2017 Red Hat, Inc, and individual contributors.
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
 * /
 */
package io.smallrye.metrics.mbean;

import org.eclipse.microprofile.metrics.Gauge;

import io.smallrye.metrics.JmxWorker;

/**
 * @author hrupp
 */
public class MGaugeImpl implements Gauge {

    private final String mBeanExpression;
    private final JmxWorker worker;

    public MGaugeImpl(JmxWorker worker, String mBeanExpression) {
        this.worker = worker;
        this.mBeanExpression = mBeanExpression;
    }

    @Override
    public Number getValue() {
        return worker.getValue(mBeanExpression);
    }
}
