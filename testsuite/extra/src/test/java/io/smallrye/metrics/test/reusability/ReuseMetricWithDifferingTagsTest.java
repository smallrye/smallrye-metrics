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

package io.smallrye.metrics.test.reusability;

import jakarta.inject.Inject;

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
 * Test that two metrics of the same name and differing tags can be created by annotations.
 */
@RunWith(Arquillian.class)
public class ReuseMetricWithDifferingTagsTest extends MeterRegistrySetup {

    @Inject
    MetricRegistry metricRegistry;

    @Inject
    private ReuseMetricWithDifferingTagsBean bean;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(ReuseMetricWithDifferingTagsBean.class);
    }

    @Test
    public void test() {
        bean.colorBlue1();
        bean.colorBlue1();
        bean.colorBlue2();
        bean.colorRed1();
        bean.colorRed2();
        Assert.assertEquals(3,
                metricRegistry.getCounters().get(new MetricID("colorCounter", new Tag("color", "blue"))).getCount());
        Assert.assertEquals(2,
                metricRegistry.getCounters().get(new MetricID("colorCounter", new Tag("color", "red"))).getCount());
    }

}
