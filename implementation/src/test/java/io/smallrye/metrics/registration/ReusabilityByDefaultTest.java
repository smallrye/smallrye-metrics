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

package io.smallrye.metrics.registration;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

/**
 * Verify that programmatically created metrics can be reused by default.
 */
public class ReusabilityByDefaultTest {

    private final MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanup() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Test
    public void testCounter() {
        registry.counter("mycounter").inc(1);
        registry.counter("mycounter").inc(1);
        assertEquals(2, registry.counter("mycounter").getCount());
    }

    @Test
    public void testHistogram() {
        registry.histogram("myhistogram").update(5);
        registry.histogram("myhistogram").update(3);
        assertEquals(3, registry.histogram("myhistogram").getSnapshot().getMin());
    }

    @Test
    public void testSimpleTimer() {
        Metadata metadata = Metadata.builder().withName("mysimpletimer").build();
        registry.simpleTimer(metadata).update(Duration.ofNanos(5));
        registry.simpleTimer(metadata).update(Duration.ofNanos(7));
        assertEquals(2, registry.simpleTimer("mysimpletimer").getCount());
    }

    @Test
    public void testTimer() {
        Metadata metadata = Metadata.builder().withName("mytimer").build();
        registry.timer(metadata).update(Duration.ofNanos(5));
        registry.timer(metadata).update(Duration.ofNanos(7));
        assertEquals(7, registry.timer("mytimer").getSnapshot().getMax());
    }

    @Test
    public void testMeter() {
        Metadata metadata = Metadata.builder().withName("mymeter").build();
        registry.meter(metadata).mark(100);
        registry.meter(metadata).mark(100);
        assertEquals(200, registry.meter("mymeter").getCount());
    }

    @Test
    public void testConcurrentGauge() {
        Metadata metadata = Metadata.builder().withName("mycgauge").withUnit(MetricUnits.SECONDS).build();
        try {
            registry.concurrentGauge(metadata).inc();
            registry.concurrentGauge(metadata).inc();
            assertEquals(2, registry.concurrentGauge("mycgauge").getCount());
        } finally {
            registry.concurrentGauge(metadata).dec();
            registry.concurrentGauge(metadata).dec();
        }
    }

    /**
     * Gauges should not be reusable.
     * However, verify that after removing a gauge, it is possible to register a new one.
     */
    @Test
    public void gaugeReregistration() {
        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.GAUGE)
                .withName("mygauge")
                .build();
        registry.register(metadata, (Gauge<Long>) () -> 42L);
        assertEquals(42L, registry.getGauges().get(new MetricID("mygauge")).getValue());
        try {
            registry.register(metadata, (Gauge<Long>) () -> 43L);
            Assert.fail("Should not be able to re-register a gauge");
        } catch (IllegalArgumentException ex) {
            // OK
        }

        // after removing the gauge, I should be able to register it again with different metadata and implementation object
        registry.remove("mygauge");

        Metadata metadata2 = Metadata.builder()
                .withType(MetricType.GAUGE)
                .withName("mygauge")
                .withDescription("My awesome gauge")
                .build();
        registry.register(metadata2, (Gauge<Long>) () -> 45L);
        assertEquals(45L, registry.getGauges().get(new MetricID("mygauge")).getValue());
    }

}
