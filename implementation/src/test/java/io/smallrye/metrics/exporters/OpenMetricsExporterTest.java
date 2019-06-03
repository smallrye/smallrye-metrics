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

package io.smallrye.metrics.exporters;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.TimerImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.smallrye.metrics.exporters.OpenMetricsExporter.getOpenMetricsMetricName;
import static java.util.regex.Pattern.quote;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class OpenMetricsExporterTest {

    private static final String LINE_SEPARATOR = "\n";

    private Tag QUANTILE_0_5 = new Tag("quantile", "0.5");
    private Tag QUANTILE_0_75 = new Tag("quantile", "0.75");
    private Tag QUANTILE_0_95 = new Tag("quantile", "0.95");
    private Tag QUANTILE_0_98 = new Tag("quantile", "0.98");
    private Tag QUANTILE_0_99 = new Tag("quantile", "0.99");
    private Tag QUANTILE_0_999 = new Tag("quantile", "0.999");


    @After
    public void cleanupApplicationMetrics() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    @Test
    public void testUptimeGaugeUnitConversion() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry baseRegistry = MetricRegistries.get(MetricRegistry.Type.BASE);

        Gauge gauge = new MGaugeImpl(JmxWorker.instance(), "java.lang:type=Runtime/Uptime");
        Metadata metadata = new ExtendedMetadata("jvm.uptime", "display name", "description", MetricType.GAUGE, "milliseconds");
        baseRegistry.register(metadata, gauge);

        long actualUptime /* in ms */ = ManagementFactory.getRuntimeMXBean().getUptime();
        double actualUptimeInSeconds = actualUptime / 1000.0;

        StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.BASE, new MetricID("jvm.uptime"));
        assertNotNull(out);

        double valueFromOpenMetrics = -1;
        for (String line : out.toString().split(System.getProperty("line.separator"))) {
            if (line.startsWith("base_jvm_uptime_seconds")) {
                valueFromOpenMetrics /* in seconds */ = Double.valueOf(line.substring("base:jvm_uptime_seconds".length()).trim());
            }
        }
        assertTrue("Value should not be -1", valueFromOpenMetrics != -1) ;
        assertTrue(valueFromOpenMetrics >= actualUptimeInSeconds);
    }

    @Test
    public void metricNameConversion() {
        assertEquals("FRAG3", getOpenMetricsMetricName("FRAG3"));
        assertEquals("UNICAST3", getOpenMetricsMetricName("UNICAST3"));

        assertEquals("FOO_BAR", getOpenMetricsMetricName("FOO-BAR"));
        assertEquals("FooBAR", getOpenMetricsMetricName("FooBAR"));
        assertEquals("FooBar", getOpenMetricsMetricName("FooBar"));

        assertEquals("a_a", getOpenMetricsMetricName("a__a"));
        assertEquals("fooBar_blaBla", getOpenMetricsMetricName("fooBar_blaBla"));
    }

    @Test
    public void testExportOfDifferentMeterImplementations() {

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Meter
        Meter[] meters = { new MeterImpl(), new SomeMeter() };
        int idx = 0;
        for (Meter m : meters) {
            String name = "meter_" + idx++;
            applicationRegistry.register(name, m);
            String out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name)).toString();
            String expectedLine = "application_" + name + "_total 0.0";
            assertThat(out, containsString(expectedLine));
        }
    }

    @Test
    public void testExportOfDifferentHistogramImplementations() {

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Histogram
        Histogram[] histograms = { new HistogramImpl(new ExponentiallyDecayingReservoir()), new SomeHistogram() };
        int idx = 0;
        for (Histogram h : histograms) {
            String name = "histo_" + idx++;
            applicationRegistry.register(name, h);
            String out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name)).toString();
            String expectedLine = "application_" + name + "_mean 0.0";
            assertThat(out, containsString(expectedLine));
        }
    }

    @Test
    public void testExportOfDifferentTimerImplementations() {

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Timer
        Timer[] timers = { new TimerImpl(), new SomeTimer() };
        int idx = 0;
        for (Timer t : timers) {
            String name = "json_timer_" + idx++;
            applicationRegistry.register(name, t);
            String out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name)).toString();
            String expectedLine = "application_" + name + "_rate_per_second 0.0";
            assertThat(out, containsString(expectedLine));
        }
    }


    @Test
    public void testTagValueQuoting() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // quotes: a"b" should become a\"b\"
        Tag tag = new Tag("tag1", "a\"b\"");
        registry.counter("counter1", tag);
        String export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter1", tag)).toString();
        assertThat(export, containsString("{tag1=\"a\\\"b\\\"\"}"));

        // newline character: a\nb should stay as a\nb
        tag = new Tag("tag1", "a\\nb");
        registry.counter("counter2", tag);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter2", tag)).toString();
        assertThat(export, containsString("{tag1=\"a\\nb\"}"));

        // backslash: b\c should become b\\c
        tag = new Tag("tag1", "b\\c");
        registry.counter("counter3", tag);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter3", tag)).toString();
        assertThat(export, containsString("{tag1=\"b\\\\c\"}"));

        // backslash at the end: b\ should become b\\
        tag = new Tag("tag1", "b\\");
        registry.counter("counter4", tag);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter4", tag)).toString();
        assertThat(export, containsString("{tag1=\"b\\\\\"}"));
    }

    @Test
    public void testHelpLineQuoting() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata.builder()
                .withName("counter_with_complicated_description")
                .withDescription("hhh\\ggg\\nfff\\").build();
        registry.counter(metadata);
        String export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter_with_complicated_description")).toString();

        // hhh\ggg\nfff\ should become hhh\\ggg\nfff\\
        assertThat(export, containsString("# HELP application_counter_with_complicated_description_total hhh\\\\ggg\\nfff\\\\"));

        metadata = Metadata.builder()
                .withName("counter_with_complicated_description_2")
                .withDescription("description with \"quotes\"").build();
        registry.counter(metadata);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter_with_complicated_description_2")).toString();

        // double quotes should stay unchanged
        assertThat(export, containsString("# HELP application_counter_with_complicated_description_2_total description with \"quotes\""));
    }

    /**
     * OpenMetrics exporter should only emit a HELP line if a description exists and is not empty
     */
    @Test
    public void testEmptyDescription() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata.builder()
                .withName("counter_with_empty_description")
                .withDescription("").build();
        registry.counter(metadata);
        String export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter_with_empty_description")).toString();
        assertThat(export, not(containsString("HELP")));
    }


    /**
     * In OpenMetrics exporter and counters, if the metric name does not end with _total, then _total should be appended automatically.
     * If it ends with _total, nothing extra will be appended.
     */
    @Test
    public void testAppendingOfTotal() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Tag tag = new Tag("a", "b");

        // in this case _total should be appended
        registry.counter("counter1", tag);
        String export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter1", tag)).toString();
        assertThat(export, containsString("application_counter1_total{a=\"b\"}"));

        // in this case _total should NOT be appended
        registry.counter("counter2_total", tag);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter2_total", tag)).toString();
        assertThat(export, containsString("application_counter2_total{a=\"b\"}"));

    }

    @Test
    public void exportHistograms() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.HISTOGRAM)
                .withName("MyHisto")
                .withDescription("awesome")
                .build();
        Tag blueTag = new Tag("color", "blue");
        Histogram histogram1 = registry.histogram(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Histogram histogram2 = registry.histogram(metadata, greenTag);

        histogram1.update(5);
        histogram1.update(9);
        histogram2.update(10);
        histogram2.update(12);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "MyHisto").toString();
        System.out.println(result);

        assertHasValueLineExactlyOnce(result, "application_MyHisto_min", "5.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_max", "9.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_mean", "7.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_stddev", "2.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_count", "2.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "9.0", blueTag, QUANTILE_0_999);

        assertHasValueLineExactlyOnce(result, "application_MyHisto_min", "10.0", greenTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_max", "12.0", greenTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_mean", "11.0", greenTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_stddev", "1.0", greenTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto_count", "2.0", greenTag);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "application_MyHisto", "12.0", greenTag, QUANTILE_0_999);

        assertHasTypeLineExactlyOnce(result, "application_MyHisto_min", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_MyHisto_max", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_MyHisto_mean", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_MyHisto_stddev", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_MyHisto", "summary");

        assertHasHelpLineExactlyOnce(result, "application_MyHisto", "awesome");
    }

    @Test
    public void exportCounters() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.COUNTER)
                .withName("mycounter")
                .withDescription("awesome")
                .build();
        Tag blueTag = new Tag("color", "blue");
        Counter blueCounter = registry.counter(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Counter greenCounter = registry.counter(metadata, greenTag);

        blueCounter.inc(10);
        greenCounter.inc(20);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mycounter").toString();
        System.out.println(result);

        assertHasTypeLineExactlyOnce(result, "application_mycounter_total", "counter");
        assertHasHelpLineExactlyOnce(result, "application_mycounter_total", "awesome");

        assertHasValueLineExactlyOnce(result, "application_mycounter_total", "10.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mycounter_total", "20.0", greenTag);

    }

    @Test
    public void exportGauges() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.GAUGE)
                .withName("mygauge")
                .withDescription("awesome")
                .build();
        Tag blueTag = new Tag("color", "blue");
        registry.register(metadata, (Gauge<Long>) () -> 42L, blueTag);
        Tag greenTag = new Tag("color", "green");
        registry.register(metadata, (Gauge<Long>) () -> 26L, greenTag);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mygauge").toString();
        System.out.println(result);

        assertHasTypeLineExactlyOnce(result, "application_mygauge", "gauge");
        assertHasHelpLineExactlyOnce(result, "application_mygauge", "awesome");

        assertHasValueLineExactlyOnce(result, "application_mygauge", "42.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mygauge", "26.0", greenTag);
    }

    @Test
    public void exportConcurrentGauges() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.CONCURRENT_GAUGE)
                .withName("myconcurrentgauge")
                .withDescription("awesome")
                .withUnit("dollars")  // this should get ignored and should not be reflected in the output
                .build();
        Tag blueTag = new Tag("color", "blue");
        ConcurrentGauge blueCGauge = registry.concurrentGauge(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        ConcurrentGauge greenCGauge = registry.concurrentGauge(metadata, greenTag);

        blueCGauge.inc();
        blueCGauge.inc();
        greenCGauge.inc();

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "myconcurrentgauge").toString();
        System.out.println(result);

        assertHasTypeLineExactlyOnce(result, "application_myconcurrentgauge_current", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_myconcurrentgauge_min", "gauge");
        assertHasTypeLineExactlyOnce(result, "application_myconcurrentgauge_max", "gauge");
        assertHasHelpLineExactlyOnce(result, "application_myconcurrentgauge_current", "awesome");

        assertHasValueLineExactlyOnce(result, "application_myconcurrentgauge_current", "2.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_myconcurrentgauge_current", "1.0", greenTag);
    }

    @Test
    public void exportMeters() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.METERED)
                .withName("mymeter")
                .withDescription("awesome")
                .build();
        Tag blueTag = new Tag("color", "blue");
        Meter blueMeter = registry.meter(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Meter greenMeter = registry.meter(metadata, greenTag);

        blueMeter.mark(20);
        greenMeter.mark(10);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mymeter").toString();
        System.out.println(result);

        assertHasTypeLineExactlyOnce(result, "application_mymeter_total", "counter");
        assertHasHelpLineExactlyOnce(result, "application_mymeter_total", "awesome");
        assertHasValueLineExactlyOnce(result, "application_mymeter_total", "20.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mymeter_total", "10.0", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mymeter_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mymeter_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mymeter_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mymeter_one_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mymeter_one_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mymeter_one_min_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mymeter_five_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mymeter_five_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mymeter_five_min_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mymeter_fifteen_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mymeter_fifteen_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mymeter_fifteen_min_rate_per_second", "*", greenTag);
    }

    @Test
    public void exportTimers() {
        OpenMetricsExporter exporter = new OpenMetricsExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metadata metadata = Metadata
                .builder()
                .withType(MetricType.TIMER)
                .withName("mytimer")
                .withDescription("awesome")
                .build();
        Tag blueTag = new Tag("color", "blue");
        Timer blueTimer = registry.timer(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Timer greenTimer = registry.timer(metadata, greenTag);

        blueTimer.update(3, TimeUnit.SECONDS);
        blueTimer.update(4, TimeUnit.SECONDS);
        greenTimer.update(5, TimeUnit.SECONDS);
        greenTimer.update(6, TimeUnit.SECONDS);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mytimer").toString();
        System.out.println(result);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_seconds", "summary");
        assertHasHelpLineExactlyOnce(result, "application_mytimer_seconds", "awesome");

        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds_count", "2.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds_count", "2.0", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_min_seconds", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_min_seconds", "3.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_min_seconds", "5.0", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_max_seconds", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_max_seconds", "4.0", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_max_seconds", "6.0", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_mean_seconds", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_mean_seconds", "3.5", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_mean_seconds", "5.5", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_stddev_seconds", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_stddev_seconds", "0.5", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_stddev_seconds", "0.5", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_one_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_one_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_one_min_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_five_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_five_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_five_min_rate_per_second", "*", greenTag);

        assertHasTypeLineExactlyOnce(result, "application_mytimer_fifteen_min_rate_per_second", "gauge");
        assertHasValueLineExactlyOnce(result, "application_mytimer_fifteen_min_rate_per_second", "*", blueTag);
        assertHasValueLineExactlyOnce(result, "application_mytimer_fifteen_min_rate_per_second", "*", greenTag);

        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "4.0", blueTag, QUANTILE_0_999);

        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "application_mytimer_seconds", "6.0", greenTag, QUANTILE_0_999);
    }

    private void assertHasValueLineExactlyOnce(String output, String metricName, String value, Tag... tags) {
        List<String> foundLines = getLines(output, metricName, value, tags);
        if(foundLines.isEmpty())
            Assert.fail("Couldn't find a line with metricName=" + metricName
                + ", value=" + value
                + ", tags=" + Arrays.toString(tags)
                + " in the OpenMetrics output: \n" + output);
        if(foundLines.size() > 1)
            Assert.fail("Found metricName=" + metricName
                    + ", value=" + value
                    + ", tags=" + Arrays.toString(tags)
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    private void assertHasTypeLineExactlyOnce(String output, String metricName, String type) {
        List<String> foundLines = getLinesByRegex(output, "# TYPE " + quote(metricName) + " " + quote(type));
        if(foundLines.isEmpty())
            Assert.fail("Couldn't find a TYPE line with metricName=" + metricName
                    + " and type=" + type
                    + " in the OpenMetrics output: \n" + output);
        if(foundLines.size() > 1)
            Assert.fail("Found TYPE line with metricName=" + metricName
                    + " and type=" + type
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    private void assertHasHelpLineExactlyOnce(String output, String metricName, String help) {
        List<String> foundLines = getLinesByRegex(output, "# HELP " + quote(metricName) + " " + quote(help));
        if(foundLines.isEmpty())
            Assert.fail("Couldn't find a HELP line with metricName=" + metricName
                    + " and help=" + help
                    + " in the OpenMetrics output: \n" + output);
        if(foundLines.size() > 1)
            Assert.fail("Found HELP line with metricName=" + metricName
                    + " and help=" + help
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    /**
     * get a line from an OpenMetrics output which contains a metric with the specified name, value and tags
     * (order of the tags doesn't matter)
     */
    private List<String> getLines(String output, String metricName, String value, Tag... tags) {
        return Arrays.stream(output.split("\n"))
                // filter by metric name at the beginning of the line
                .filter(line -> line.matches(quote(metricName) + "\\{.+"))
                // filter by metric value at the end of the line
                .filter(line -> line.matches(".+} " + quote(value)) || value.equals("*"))
                // filter by present tags
                .filter(line -> {
                    boolean matches = true;
                    for(Tag tag : tags) {
                        if(!line.matches(".+" + quote(tag.getTagName()) + "=\"" + quote(tag.getTagValue()) +"\".+"))
                            matches = false;
                    }
                    return matches;
                })
                .collect(Collectors.toList());
    }

    private List<String> getLinesByRegex(String output, String regex) {
        return Arrays.stream(output.split("\n"))
                .filter(line -> line.matches(regex))
                .collect(Collectors.toList());
    }
}
