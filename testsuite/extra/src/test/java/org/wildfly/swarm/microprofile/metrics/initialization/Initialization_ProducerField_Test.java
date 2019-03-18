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

package org.wildfly.swarm.microprofile.metrics.initialization;

import io.smallrye.metrics.app.CounterImpl;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class Initialization_ProducerField_Test {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(BeanWithMetricProducerField.class);
    }

    @Inject
    MetricRegistry registry;

    @Inject
    BeanWithMetricProducerField bean;

    @Test
    public void test() {
        String name = "counter1";
        // check eager initialization here
        assertTrue(registry.getCounters().containsKey(name));
        assertEquals(0, registry.getCounters().get(name).getCount());
        bean.addDataToCounter();
        Counter counter = registry.getCounters().get(name);
        assertEquals(1, counter.getCount());
    }

    public static class BeanWithMetricProducerField {

        @Inject
        MetricRegistry registry;

        @Produces
        @ApplicationScoped
        @Metric(name = "counter1", absolute = true)
        Counter c1 = new CounterImpl();

        public void addDataToCounter() {
            registry.getCounters().get("counter1").inc();
        }

    }

}
