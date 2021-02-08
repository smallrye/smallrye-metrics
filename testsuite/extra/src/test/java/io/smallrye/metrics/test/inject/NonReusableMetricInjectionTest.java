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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.test.MeterRegistrySetup;

/**
 * Test that it is possible to inject a metric using an annotated method parameter.
 */
@RunWith(Arquillian.class)
public class NonReusableMetricInjectionTest extends MeterRegistrySetup {

    @Inject
    private Instance<NonReusableMetricInjectionBean> bean;

    @Inject
    private MetricRegistry metricRegistry;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(NonReusableMetricInjectionBean.class);
    }

    @Test
    public void test() {
        // force creating two instances of the bean
        bean.get();
        bean.get();
        Assert.assertEquals(4, metricRegistry.getCounters().get(new MetricID("mycounter", new Tag("k", "v1"))).getCount());
        Assert.assertEquals(6, metricRegistry.getCounters().get(new MetricID("mycounter", new Tag("k", "v2"))).getCount());
    }

}
