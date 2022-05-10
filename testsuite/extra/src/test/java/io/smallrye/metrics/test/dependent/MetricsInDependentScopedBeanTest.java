/*
 * Copyright 2019 Red Hat, Inc, and individual contributors.
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

package io.smallrye.metrics.test.dependent;

import static org.junit.Assert.assertEquals;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.MetricRegistries;

/**
 * Verify that it is possible to have metrics (except gauges) in beans with other scope than just ApplicationScope.
 * If there are multiple instances of the bean, the metrics should be automatically added up together over all instances.
 * They don't need to be marked as reusable for this.
 * This does not work for gauges because a gauge must always be bound to just one object.
 */
@RunWith(Arquillian.class)
public class MetricsInDependentScopedBeanTest {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(DependentScopedBeanWithMetrics.class);
    }

    @AfterClass
    public static void cleanupApplicationMetrics() {
        MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    @Inject
    private Instance<DependentScopedBeanWithMetrics> beanInstance;

    @Inject
    private MetricRegistry registry;

    @Test
    public void counter() {
        DependentScopedBeanWithMetrics instance1 = beanInstance.get();
        DependentScopedBeanWithMetrics instance2 = beanInstance.get();

        instance1.countedMethod();
        instance2.countedMethod();

        assertEquals(2, registry.getCounters().get(new MetricID("counter")).getCount());
    }

    @Test
    public void meter() {
        DependentScopedBeanWithMetrics instance1 = beanInstance.get();
        DependentScopedBeanWithMetrics instance2 = beanInstance.get();

        instance1.meteredMethod();
        instance2.meteredMethod();

        assertEquals(2, registry.getMeters().get(new MetricID("meter")).getCount());
    }

    @Test
    public void timer() {
        DependentScopedBeanWithMetrics instance1 = beanInstance.get();
        DependentScopedBeanWithMetrics instance2 = beanInstance.get();

        instance1.timedMethod();
        instance2.timedMethod();

        assertEquals(2, registry.getTimers().get(new MetricID("timer")).getCount());
    }

    @Test
    public void concurrentGauge() {
        DependentScopedBeanWithMetrics instance1 = beanInstance.get();
        DependentScopedBeanWithMetrics instance2 = beanInstance.get();

        instance1.cGaugedMethod();
        instance2.cGaugedMethod();

        assertEquals(0, registry.getConcurrentGauges().get(new MetricID("cgauge")).getCount());
    }

}
