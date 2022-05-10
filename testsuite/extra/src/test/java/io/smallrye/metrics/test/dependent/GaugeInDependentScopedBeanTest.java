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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.MetricRegistries;

/**
 * Gauges can only be used with AnnotationScoped beans. If we place a gauge on a bean that creates multiple
 * instances during the application lifetime, this should be treated as an error, because a gauge
 * must always be bound to just one object, therefore creating multiple instances of the bean would
 * create ambiguity.
 */
@RunWith(Arquillian.class)
public class GaugeInDependentScopedBeanTest {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(DependentScopedBeanWithGauge.class);
    }

    @After
    public void cleanupApplicationMetrics() {
        MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    @Inject
    private Instance<DependentScopedBeanWithGauge> beanInstance;

    @Test
    public void gauge() {
        try {
            DependentScopedBeanWithGauge instance1 = beanInstance.get();
            DependentScopedBeanWithGauge instance2 = beanInstance.get();
            Assert.fail("Shouldn't be able to create multiple instances of a bean that contains a gauge");
        } catch (Exception e) {

        }
    }

}
