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

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class Initialization_Gauge_Method_Test {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(BeanWithGauge_ApplicationScoped.class);
    }

    @Inject
    MetricRegistry registry;

    @Inject
    BeanWithGauge_ApplicationScoped applicationScopedBean;


    /**
     * With a gauge in an application-scoped bean, the metric will be registered once the bean is instantiated.
     */
    @Test
    public void testApplicationScoped() {
        applicationScopedBean.gauge(); // access the application-scoped bean so that an instance gets created
        assertTrue(registry.getGauges().containsKey(new MetricID("gaugeApp")));
        Assert.assertEquals(2L, registry.getGauges().get(new MetricID("gaugeApp")).getValue());
        Assert.assertEquals(3L, registry.getGauges().get(new MetricID("gaugeApp")).getValue());
    }

    @ApplicationScoped
    public static class BeanWithGauge_ApplicationScoped {

        Long i = 0L;

        @Gauge(name = "gaugeApp", absolute = true, unit = MetricUnits.NONE)
        public Long gauge() {
            return ++i;
        }

    }

}
