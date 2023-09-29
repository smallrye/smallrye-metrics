package io.smallrye.metrics.configuration;

import java.time.Duration;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.metrics.setup.config.DefaultBucketConfiguration;
import io.smallrye.metrics.setup.config.HistogramBucketMaxConfiguration;
import io.smallrye.metrics.setup.config.HistogramBucketMinConfiguration;
import io.smallrye.metrics.setup.config.TimerBucketMaxConfiguration;
import io.smallrye.metrics.setup.config.TimerBucketMinConfiguration;

public class DefaultBucketTest {

    @Test
    public void testGoodConfiguration() {
        String input = "test.metric1=true;test.metric2=false";

        Collection<DefaultBucketConfiguration> collection = DefaultBucketConfiguration.parse(input);

        DefaultBucketConfiguration dbc = DefaultBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(dbc.isEnabled(), true);

        dbc = DefaultBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(dbc.isEnabled(), false);

    }

    @Test
    public void testNonExistentConfiguration() {
        String input = "test.metric1=true;test.metric2=false";

        Collection<DefaultBucketConfiguration> collection = DefaultBucketConfiguration.parse(input);

        DefaultBucketConfiguration dbc = DefaultBucketConfiguration.matches(collection, "test.metric3");
        Assertions.assertNull(dbc);

    }

    @Test
    public void testOverride1() {
        String input = "test.*=false;test.metric1=true";

        Collection<DefaultBucketConfiguration> collection = DefaultBucketConfiguration.parse(input);

        DefaultBucketConfiguration dbc = DefaultBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(dbc.isEnabled(), true);

        dbc = DefaultBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(dbc.isEnabled(), false);

    }

    @Test
    public void testOverride2() {
        String input = "test.metric1=true;test.*=false";

        Collection<DefaultBucketConfiguration> collection = DefaultBucketConfiguration.parse(input);

        DefaultBucketConfiguration dbc = DefaultBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(dbc.isEnabled(), false);

        dbc = DefaultBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(dbc.isEnabled(), false);

    }

    @Test
    public void testBadValue1() {
        String input = "test.metric1=sdf";

        Collection<DefaultBucketConfiguration> collection = DefaultBucketConfiguration.parse(input);

        DefaultBucketConfiguration dbc = DefaultBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(dbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(dbc.isEnabled(), false);

    }

    /*
     * Test Histogram min and max.
     *
     *
     */
    @Test
    public void testHistogramBucketMaxMinGoodConfig() {
        String input = "test.metric1=23;test.metric2=45";

        /*
         * MAX
         */
        Collection<HistogramBucketMaxConfiguration> collectionMax = HistogramBucketMaxConfiguration.parse(input);

        HistogramBucketMaxConfiguration hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertEquals(hbmaxc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(hbmaxc.getValue(), 23.0);

        hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertEquals(hbmaxc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(hbmaxc.getValue(), 45.0);

        /*
         * MIN
         */
        Collection<HistogramBucketMinConfiguration> collectionMin = HistogramBucketMinConfiguration.parse(input);

        HistogramBucketMinConfiguration hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric1");
        Assertions.assertEquals(hbmincc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(hbmincc.getValue(), 23.0);

        hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertEquals(hbmincc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(hbmincc.getValue(), 45.0);

    }

    @Test
    public void testHistogramBucketMaxMinNonExistentConfig() {
        String input = "test.metric1=23;test.metric2=45";

        /*
         * MAX
         */
        Collection<HistogramBucketMaxConfiguration> collectionMax = HistogramBucketMaxConfiguration.parse(input);

        HistogramBucketMaxConfiguration hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric3");
        Assertions.assertNull(hbmaxc);

        /*
         * MIN
         */
        Collection<HistogramBucketMinConfiguration> collectionMin = HistogramBucketMinConfiguration.parse(input);

        HistogramBucketMinConfiguration hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric3");
        Assertions.assertNull(hbmincc);

    }

    @Test
    public void testHistogramBucketMaxMinOverride() {
        String input = "test.metric1=34.0;test.*=23;test.metric2=45";

        /*
         * MAX
         */
        Collection<HistogramBucketMaxConfiguration> collectionMax = HistogramBucketMaxConfiguration.parse(input);

        HistogramBucketMaxConfiguration hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertEquals(hbmaxc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(hbmaxc.getValue(), 23.0);

        hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertEquals(hbmaxc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(hbmaxc.getValue(), 45.0);

        /*
         * MIN
         */
        Collection<HistogramBucketMinConfiguration> collectionMin = HistogramBucketMinConfiguration.parse(input);

        HistogramBucketMinConfiguration hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric1");
        Assertions.assertEquals(hbmincc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(hbmincc.getValue(), 23.0);

        hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertEquals(hbmincc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(hbmincc.getValue(), 45.0);

    }

    @Test
    public void testHistogramBucketMaxMinBadConfig() {

        /*
         * test.metric1 -> not valid, does not accept any of the values
         * test.metric2 -> not valid
         */
        String input = "test.metric1=34.0,23;test.metric2=abc";

        /*
         * MAX
         */
        Collection<HistogramBucketMaxConfiguration> collectionMax = HistogramBucketMaxConfiguration.parse(input);

        HistogramBucketMaxConfiguration hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertNull(hbmaxc);

        hbmaxc = HistogramBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertNull(hbmaxc);

        /*
         * MIN
         */
        Collection<HistogramBucketMinConfiguration> collectionMin = HistogramBucketMinConfiguration.parse(input);

        HistogramBucketMinConfiguration hbmincc = HistogramBucketMinConfiguration.matches(collectionMin,
                "test.metric1");
        Assertions.assertNull(hbmincc);

        hbmincc = HistogramBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertNull(hbmincc);

    }

    /*
     * Test Timer min and max.
     *
     */
    @Test
    public void testTimerBucketMaxMinGoodConfig() {
        String input = "test.metric1=23;test.metric2=45";

        /*
         * MAX
         */
        Collection<TimerBucketMaxConfiguration> collectionMax = TimerBucketMaxConfiguration.parse(input);

        TimerBucketMaxConfiguration tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertEquals(tbmaxc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(tbmaxc.getValue(), Duration.ofMillis(23));

        tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertEquals(tbmaxc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(tbmaxc.getValue(), Duration.ofMillis(45));

        /*
         * MIN
         */
        Collection<TimerBucketMinConfiguration> collectionMin = TimerBucketMinConfiguration.parse(input);

        TimerBucketMinConfiguration tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric1");
        Assertions.assertEquals(tbmincc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertEquals(tbmincc.getValue(), Duration.ofMillis(23));

        tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertEquals(tbmincc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(tbmincc.getValue(), Duration.ofMillis(45));

    }

    @Test
    public void testTimerBucketMaxMinNonExistentConfig() {
        String input = "test.metric1=23;test.metric2=45";

        /*
         * MAX
         */
        Collection<TimerBucketMaxConfiguration> collectionMax = TimerBucketMaxConfiguration.parse(input);

        TimerBucketMaxConfiguration tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric3");
        Assertions.assertNull(tbmaxc);

        /*
         * MIN
         */
        Collection<TimerBucketMinConfiguration> collectionMin = TimerBucketMinConfiguration.parse(input);

        TimerBucketMinConfiguration tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric3");
        Assertions.assertNull(tbmincc);

    }

    @Test
    public void testTimerBucketMaxMinOverride() {
        String input = "test.metric1=34ms;test.*=23;test.metric2=45s";

        /*
         * MAX
         */
        Collection<TimerBucketMaxConfiguration> collectionMax = TimerBucketMaxConfiguration.parse(input);

        TimerBucketMaxConfiguration tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertEquals(tbmaxc.getMetricNameGrouping(), "test.*");

        Assertions.assertEquals(tbmaxc.getValue(), Duration.ofMillis(23));

        tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertEquals(tbmaxc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(tbmaxc.getValue(), Duration.ofSeconds(45));

        /*
         * MIN
         */
        Collection<TimerBucketMinConfiguration> collectionMin = TimerBucketMinConfiguration.parse(input);

        TimerBucketMinConfiguration tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric1");
        Assertions.assertEquals(tbmincc.getMetricNameGrouping(), "test.*");
        Assertions.assertEquals(tbmincc.getValue(), Duration.ofMillis(23));

        tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertEquals(tbmincc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertEquals(tbmincc.getValue(), Duration.ofSeconds(45));

    }

    @Test
    public void testTimerBucketMaxMinBadConfig() {

        /*
         * test.metric1 -> not valid, does not accept any of the values
         * test.metric2 -> not valid
         */
        String input = "test.metric1=34s,23ms;test.metric2=abc";

        /*
         * MAX
         */
        Collection<TimerBucketMaxConfiguration> collectionMax = TimerBucketMaxConfiguration.parse(input);

        TimerBucketMaxConfiguration tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric1");
        Assertions.assertNull(tbmaxc);

        tbmaxc = TimerBucketMaxConfiguration.matches(collectionMax, "test.metric2");
        Assertions.assertNull(tbmaxc);

        /*
         * MIN
         */
        Collection<TimerBucketMinConfiguration> collectionMin = TimerBucketMinConfiguration.parse(input);

        TimerBucketMinConfiguration tbmincc = TimerBucketMinConfiguration.matches(collectionMin,
                "test.metric1");
        Assertions.assertNull(tbmincc);

        tbmincc = TimerBucketMinConfiguration.matches(collectionMin, "test.metric2");
        Assertions.assertNull(tbmincc);

    }
}
