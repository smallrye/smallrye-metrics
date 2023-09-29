package io.smallrye.metrics.configuration;

import java.time.Duration;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.metrics.setup.config.TimerBucketConfiguration;

public class TimerConfigurationTest {

    @Test
    public void testGoodConfiguration() {
        String input = "test.metric1=523ms,2s,1m,3h;test.metric2=600,345ms,45,1s";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");

        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(523), Duration.ofSeconds(2),
                Duration.ofMinutes(1), Duration.ofHours(3) });

        tbc = TimerBucketConfiguration.matches(collection, "test.metric2");

        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(45), Duration.ofMillis(345),
                Duration.ofMillis(600), Duration.ofSeconds(1) });

    }

    @Test
    public void testNonExistentvalue() {
        String input = "test.metric1=523ms,2s,1m,3h;test.metric2=600,345ms,45,1s";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric3");

        Assertions.assertNull(tbc);
    }

    @Test
    public void testOverride1() {
        String input = "test.metric1=523ms,2s,1m,3h;test.metric2=45;test.sub.metric1=78s,1s;test.*=99";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(99) });

        tbc = TimerBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(99) });

        tbc = TimerBucketConfiguration.matches(collection, "test.sub.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(99) });
    }

    @Test
    public void testOverride2() {
        String input = "test.*=99;test.metric1=850ms,2s;test.metric2=45;test.sub.metric1=78s,1s";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(850), Duration.ofSeconds(2) });

        tbc = TimerBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(45) });

        tbc = TimerBucketConfiguration.matches(collection, "test.sub.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.sub.metric1");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofSeconds(1), Duration.ofSeconds(78) });

        tbc = TimerBucketConfiguration.matches(collection, "test.other.metric");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(99) });
    }

    @Test
    public void testOverride3() {
        String input = "*=123ms;test.*=45ms,1s";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(45), Duration.ofSeconds(1) });

        tbc = TimerBucketConfiguration.matches(collection, "other.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(123) });

    }

    @Test
    public void testBadValue1() {

        /*
         * test.metric1 has no valid values
         * test.metric2 hs valid value for 34(ms)
         */
        String input = "test.metric1=12.0ms;test.metric2=34fj,78.0s,*(ms,fjm,34";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] {});

        tbc = TimerBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] { Duration.ofMillis(34) });

    }

    @Test
    public void testBadValue2() {
        String input = "test.metric1=test.metric2=12ms";

        Collection<TimerBucketConfiguration> collection = TimerBucketConfiguration.parse(input);

        TimerBucketConfiguration tbc = TimerBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(tbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(tbc.getValues(), new Duration[] {});

        tbc = TimerBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertNull(tbc);

    }
}
