package io.smallrye.metrics.configuration;

import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.metrics.setup.config.HistogramBucketConfiguration;

public class HistogramConfigurationTest {

    @Test
    public void testGoodConfiguration() {
        String input = "test.metric1=0.2,0.45,123,345,678,1239;metric2=123,56,0.96";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 0.2, 0.45, 123.0, 345.0, 678.0, 1239.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "metric2");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "metric2");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 0.96, 56.0, 123.0 });

    }

    @Test
    public void testNonExistentValue() {
        String input = "test.metric1=0.2,0.45,123,345,678,1239;metric2=123,56,0.96";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric9");
        Assertions.assertNull(hbc);

    }

    @Test
    public void testOverride1() {
        String input = "test.metric1=1,6,9;test.metric2=0.2,45,99;test.sub.metric=1;*=12,34,56";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 12.0, 34.0, 56.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 12.0, 34.0, 56.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.sub.metric");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 12.0, 34.0, 56.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "any.value");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 12.0, 34.0, 56.0 });

    }

    @Test
    public void testOverride2() {
        String input = "*=12,34,56;test.metric1=1,6,9;test.metric2=0.2,45,99;test.sub.metric=1";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 1.0, 6.0, 9.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 0.2, 45.0, 99.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.sub.metric");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.sub.metric");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 1.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "any.value");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 12.0, 34.0, 56.0 });

    }

    @Test
    public void testBadValue1() {

        /*
         * test.metric1 has valid 0.5, 33.0, 34.0
         * test.metric2 has no valid values
         */
        String input = "test.metric1=12s,234m,-0.2,0.5,34,e3,43f,33;test.metric2=345s,dg,#$G#$,#*(45";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 0.5, 33.0, 34.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] {});

    }

    @Test
    public void testBadValue2() {
        String input = "test.metric1=34,test.metric2=34;test.metric3;test.metric2=";

        Collection<HistogramBucketConfiguration> collection = HistogramBucketConfiguration.parse(input);

        HistogramBucketConfiguration hbc = HistogramBucketConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(hbc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(hbc.getValues(), new Double[] { 34.0 });

        hbc = HistogramBucketConfiguration.matches(collection, "test.metric2");
        Assertions.assertNull(hbc);

        hbc = HistogramBucketConfiguration.matches(collection, "test.metric3");
        Assertions.assertNull(hbc);
    }
}
