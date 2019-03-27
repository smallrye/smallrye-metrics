/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
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
package io.smallrye.metrics.test.registry;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.smallrye.metrics.test.HelloService;
import io.smallrye.metrics.test.MetricsSummary;

import javax.inject.Inject;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class AllMetricsOfGivenTypeTest {
    @Inject
    HelloService hello;

    @Inject
    MetricsSummary summary;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addPackage(HelloService.class.getPackage())
                .addPackage(AllMetricsOfGivenTypeTest.class.getPackage());
    }

    @Test
    public void testGetMetricsOfGivenType() {
        hello.hello();
        hello.howdy();
        SortedMap<MetricID, Timer> timers = summary.getAppMetrics().getTimers();
        assertEquals(1, timers.size());
        assertTrue(timers.containsKey(new MetricID("howdy-time")));
        assertFalse(timers.containsKey(new MetricID(("hello-count"))));
    }
}
