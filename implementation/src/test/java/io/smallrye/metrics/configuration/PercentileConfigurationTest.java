package io.smallrye.metrics.configuration;

import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.metrics.setup.config.MetricPercentileConfiguration;
import io.smallrye.metrics.setup.config.PropertyConfiguration;

public class PercentileConfigurationTest {

    @Test
    public void testGoodValues() {

        String input = "test.metric1=0.3,0.5,0.9;test.metric2=0.09,0.4,0.8,0.3";

        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.3, 0.5, 0.9 });

        mpc = PropertyConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.09, 0.3, 0.4, 0.8 });

    }

    @Test
    public void testNonExistingValue() {

        String input = "test.metric1=0.3,0.5,0.9;test.metric2=0.09,0.4,0.8,0.3";

        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric99");
        Assertions.assertNull(mpc);
    }

    @Test
    public void testOverride1() {

        String input = "test.*=0.5,0.6,0.99;test.metric1=0.1,0.2;test.metric2=0.3;test.metric.metric1=0.8";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.1, 0.2 });

        mpc = PropertyConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric2");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.3 });

        mpc = PropertyConfiguration.matches(collection, "test.metric3");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.test.metric");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.test.sub.metric");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.metric.metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric.metric1");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.8 });

    }

    @Test
    public void testOverride2() {

        String input = "test.metric1=0.1,0.2;test.metric2=0.3;test.metric.metric1=0.8;test.*=0.5,0.6,0.99";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.metric2");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.metric3");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.test.metric");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

        mpc = PropertyConfiguration.matches(collection, "test.metric.metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5, 0.6, 0.99 });

    }

    @Test
    public void testDisable() {

        String input = "test.metric1=";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");

        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.metric1");
        Assertions.assertTrue(mpc.isDisabled());
        Assertions.assertNull(mpc.getValues());

    }

    @Test
    public void testDisableWildcard1() {

        String input = "test.*";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");

        Assertions.assertEquals(mpc.getMetricNameGrouping(), "test.*");
        Assertions.assertTrue(mpc.isDisabled());
        Assertions.assertNull(mpc.getValues());

    }

    @Test
    public void testDisableWildcard2() {

        String input = "*=";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");

        Assertions.assertEquals(mpc.getMetricNameGrouping(), "*");
        Assertions.assertTrue(mpc.isDisabled());
        Assertions.assertNull(mpc.getValues());

    }

    @Test
    public void testDisableAll() {

        String input = "";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "test.metric1");

        Assertions.assertEquals(mpc.getMetricNameGrouping(), "*");
        Assertions.assertTrue(mpc.isDisabled());
        Assertions.assertNull(mpc.getValues());

    }

    @Test
    public void testBadValue1() {

        /*
         * metric1 has a valid 0.2
         * metric2 has no valid values
         * metric3 has a valid 0.5
         */
        String input = "metric1=23,0.2,34*(;metric2=89,34,#$,fj;metric3=1.3,2.4,0.5;metric4=sdf,$%,0..0,0.8,0,,0.9";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "metric1");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.2 });

        mpc = PropertyConfiguration.matches(collection, "metric2");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "metric2");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] {});

        mpc = PropertyConfiguration.matches(collection, "metric3");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "metric3");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.5 });

        mpc = PropertyConfiguration.matches(collection, "metric4");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "metric4");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] { 0.8, 0.9 });
    }

    @Test
    public void testBadValue2() {

        /*
         * metric1 has a valid 0.2
         * `*` overrides. No valid values for anything.
         */
        String input = "metric1=23,0.2,34*(;*=90.3,fs,)*";
        Collection<MetricPercentileConfiguration> collection = MetricPercentileConfiguration
                .parseMetricPercentiles(input);

        MetricPercentileConfiguration mpc = PropertyConfiguration.matches(collection, "metric1");
        Assertions.assertEquals(mpc.getMetricNameGrouping(), "*");
        Assertions.assertArrayEquals(mpc.getValues(), new Double[] {});

    }

}
