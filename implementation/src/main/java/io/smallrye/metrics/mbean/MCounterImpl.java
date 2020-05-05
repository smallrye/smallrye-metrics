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

import org.eclipse.microprofile.metrics.Counter;

import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.SmallRyeMetricsMessages;

/**
 * @author hrupp
 */
public class MCounterImpl implements Counter {
    private final String mbeanExpression;
    private final JmxWorker worker;

    public MCounterImpl(JmxWorker worker, String mbeanExpression) {
        this.mbeanExpression = mbeanExpression;
        this.worker = worker;
    }

    @Override
    public void inc() {
        throw SmallRyeMetricsMessages.msg.mustNotBeCalled();
    }

    @Override
    public void inc(long n) {
        throw SmallRyeMetricsMessages.msg.mustNotBeCalled();
    }

    @Override
    public long getCount() {
        return worker.getValue(mbeanExpression).longValue();
    }
}
