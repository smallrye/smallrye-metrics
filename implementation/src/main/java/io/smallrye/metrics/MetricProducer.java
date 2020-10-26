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
 *
 */
package io.smallrye.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;

@ApplicationScoped
public class MetricProducer {

    // TODO

    @Produces
    <T extends Number> Gauge<T> getGauge(InjectionPoint ip) {
        return null;
    }

    @Produces
    Counter getCounter(InjectionPoint ip) {
        return null;
    }

    @Produces
    ConcurrentGauge getConcurrentGauge(InjectionPoint ip) {
        return null;
    }

    @Produces
    Histogram getHistogram(InjectionPoint ip) {
        return null;
    }

    @Produces
    Meter getMeter(InjectionPoint ip) {
        return null;
    }

    @Produces
    Timer getTimer(InjectionPoint ip) {
        return null;
    }

    @Produces
    SimpleTimer getSimpleTimer(InjectionPoint ip) {
        return null;
    }

}
