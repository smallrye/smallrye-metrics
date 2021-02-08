/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
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

package io.smallrye.metrics.test.stereotype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.test.MeterRegistrySetup;
import io.smallrye.metrics.test.stereotype.stereotypes.CountMe;

@RunWith(Arquillian.class)
public class StereotypeCountedClassTest extends MeterRegistrySetup {

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(CountedClass.class, CountMe.class);
    }

    @Inject
    MetricRegistry metricRegistry;

    @Inject
    CountedClass bean;

    @Test
    public void test() {
        MetricID id_constructor = new MetricID(CountedClass.class.getName() + ".CountedClass");
        assertTrue(metricRegistry.getCounters().containsKey(id_constructor));
        MetricID id_method = new MetricID(CountedClass.class.getName() + ".foo");
        assertTrue(metricRegistry.getCounters().containsKey(id_method));
        bean.foo();
        assertEquals(1, metricRegistry.getCounters().get(id_method).getCount());
    }

}
