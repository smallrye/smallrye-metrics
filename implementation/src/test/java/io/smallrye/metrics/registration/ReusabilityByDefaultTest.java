package io.smallrye.metrics.registration;

import io.smallrye.metrics.MetricRegistries;
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

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

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
    public void testTimer() {
        Metadata metadata = Metadata.builder().withName("mytimer").build();
        registry.timer(metadata).update(5, TimeUnit.NANOSECONDS);
        registry.timer(metadata).update(7, TimeUnit.NANOSECONDS);
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
        } catch(IllegalArgumentException ex) {
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
