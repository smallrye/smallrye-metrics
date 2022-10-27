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

import static java.util.regex.Pattern.quote;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.smallrye.metrics.SharedMetricRegistries;

/**
 * Verifies functionality of the legacy MetricRegistry adapter - puts a
 * Prometheus MeterRegistry behind it, uses the adapter to programmatically
 * create metrics, and then verifies the Prometheus scraping output.
 */
public class MpMetricsCompatibility_Programmatic_PrometheusTest {

    private Tag QUANTILE_0_5 = new Tag("quantile", "0.5");
    private Tag QUANTILE_0_75 = new Tag("quantile", "0.75");
    private Tag QUANTILE_0_95 = new Tag("quantile", "0.95");
    private Tag QUANTILE_0_98 = new Tag("quantile", "0.98");
    private Tag QUANTILE_0_99 = new Tag("quantile", "0.99");
    private Tag QUANTILE_0_999 = new Tag("quantile", "0.999");

    private static PrometheusMeterRegistry prometheusRegistry;

    @AfterClass
    public static void cleanup() {
        SharedMetricRegistries.dropAll();
    }

    @After
    public void cleanupApplicationMetrics() {
        SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE).removeMatching(MetricFilter.ALL);
    }

    @Test
    public void testUptimeGaugeUnitConversion() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.BASE_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();

        long actualUptime /* in ms */ = ManagementFactory.getRuntimeMXBean().getUptime();
        double actualUptimeInSeconds = actualUptime / 1000.0;

        String out = exporter.exportMetricsByName(MetricRegistry.BASE_SCOPE, "jvm.uptime");
        assertNotNull(out);

        double valueFromOpenMetrics = -1;

        // jvm_uptime_seconds{mp_scope="base",tier="integration",} #.##
        for (String line : out.toString().split("\n")) {
            if (line.startsWith("jvm_uptime_seconds{mp_scope=\"base\",}")) {
                valueFromOpenMetrics /* in seconds */ = Double
                        .valueOf(line.substring("jvm_uptime_seconds{mp_scope=\"base\",}".length()).trim());
            }
        }

        assertTrue("Value should not be -1", valueFromOpenMetrics != -1);
        assertTrue(valueFromOpenMetrics >= actualUptimeInSeconds);
    }

    @Test
    public void testTagValueQuoting() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();

        // quotes: a"b" should become a\"b\"
        Tag tag = new Tag("tag1", "a\"b\"");
        registry.counter("counter1", tag);
        String export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter1").toString();
        assertThat(export, containsString("tag1=\"a\\\"b\\\"\""));

        // Comment from previous SR impl: newline character: a\nb should stay as a\nb
        // Note: Using Prom common lib, the value becomes a\\nb given the tag value
        // "a\\nb" i.e. "a\\\\nb"
        tag = new Tag("tag1", "a\\nb");
        registry.counter("counter2", tag);
        export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter2").toString();
        assertThat(export, containsString("tag1=\"a\\\\nb\""));

        // backslash: b\c should become b\\c
        tag = new Tag("tag1", "b\\c");
        registry.counter("counter3", tag);
        export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter3").toString();
        assertThat(export, containsString("tag1=\"b\\\\c\""));

        // backslash at the end: b\ should become b\\
        tag = new Tag("tag1", "b\\");
        registry.counter("counter4", tag);
        export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter4").toString();
        assertThat(export, containsString("tag1=\"b\\\\\""));
    }

    @Test
    public void testHelpLineQuoting() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Metadata metadata = Metadata.builder().withName("counter_with_complicated_description")
                .withDescription("hhh\\ggg\\nfff\\").build();
        registry.counter(metadata);
        String export = exporter
                .exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter_with_complicated_description")
                .toString();

        // Comment from previous SR impl: hhh\ggg\nfff\ should become hhh\\ggg\nfff\\
        // Note: Using Prom common lib, the value becomes hhh\\ggg\\nfff\\ given the
        // description value hhh\\ggg\\nfff\\ i.e. "hhh\\\\ggg\\\\nfff\\\\"
        assertThat(export, containsString("# HELP counter_with_complicated_description_total hhh\\\\ggg\\\\nfff\\\\"));

        metadata = Metadata.builder().withName("counter_with_complicated_description_2")
                .withDescription("description with \"quotes\"").build();
        registry.counter(metadata);
        export = exporter
                .exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter_with_complicated_description_2")
                .toString();

        // double quotes should stay unchanged
        assertThat(export,
                containsString("# HELP counter_with_complicated_description_2_total description with \"quotes\""));
    }

    /**
     * Prometheus exporter will emit HELP line even if it is empty
     */
    @Test
    public void testEmptyDescription() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Metadata metadata = Metadata.builder().withName("counter_with_empty_description").withDescription("").build();
        registry.counter(metadata);
        String export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter_with_empty_description")
                .toString();
        assertThat(export, containsString("HELP"));
    }

    /**
     * In PrometheusMetrics exporter and counters, if the metric name does not end
     * with _total, then _total should be appended automatically. If it ends with
     * _total, nothing extra will be appended.
     */

    @Test
    public void testAppendingOfTotal() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Tag tag = new Tag("a", "b");

        // in this case _total should be appended registry.counter("counter1", tag);
        registry.counter("counter1", tag);
        String export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter1").toString();
        assertThat(export, containsString("counter1_total{a=\"b\",mp_scope=\"application\",}"));

        // in this case _total should NOT be appended
        registry.counter("counter2_total", tag);
        export = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "counter2_total").toString();
        assertThat(export, containsString("counter2_total{a=\"b\",mp_scope=\"application\",}"));

    }

    @Test
    public void exportHistograms() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Metadata metadata = Metadata.builder().withName("MyHisto").withDescription("awesome").build();
        Tag blueTag = new Tag("color", "blue");
        Histogram histogram1 = registry.histogram(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Histogram histogram2 = registry.histogram(metadata, greenTag);

        Tag scopeTag = new Tag("mp_scope", "application");

        histogram1.update(5);
        histogram1.update(9);
        histogram2.update(10);
        histogram2.update(12);

        String result = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "MyHisto").toString();

        assertHasValueLineExactlyOnce(result, "MyHisto_max", "9.0", blueTag, scopeTag);
        assertHasValueLineExactlyOnce(result, "MyHisto_count", "2.0", blueTag, scopeTag);

        assertHasValueLineExactlyOnce(result, "MyHisto", "5.0", blueTag, scopeTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "MyHisto", "9.0", blueTag, scopeTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "MyHisto", "9.0", blueTag, scopeTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "MyHisto", "9.0", blueTag, scopeTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "MyHisto", "9.0", blueTag, scopeTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "MyHisto", "9.0", blueTag, scopeTag, QUANTILE_0_999);

        assertHasValueLineExactlyOnce(result, "MyHisto_max", "12.0", greenTag, scopeTag);
        assertHasValueLineExactlyOnce(result, "MyHisto_count", "2.0", greenTag, scopeTag);
        assertHasValueLineExactlyOnce(result, "MyHisto", "10.0", greenTag, scopeTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "MyHisto", "12.0", greenTag, scopeTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "MyHisto", "12.0", greenTag, scopeTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "MyHisto", "12.0", greenTag, scopeTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "MyHisto", "12.0", greenTag, scopeTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "MyHisto", "12.0", greenTag, scopeTag, QUANTILE_0_999);

        assertHasTypeLineExactlyOnce(result, "MyHisto_max", "gauge");
        assertHasTypeLineExactlyOnce(result, "MyHisto", "summary");
        assertHasHelpLineExactlyOnce(result, "MyHisto", "awesome");
    }

    @Test
    public void exportCounters() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Metadata metadata = Metadata.builder().withName("mycounter").withDescription("awesome").build();

        Tag blueTag = new Tag("color", "blue");
        Counter blueCounter = registry.counter(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Counter greenCounter = registry.counter(metadata, greenTag);

        blueCounter.inc(10);
        greenCounter.inc(20);

        String result = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "mycounter").toString();

        assertHasTypeLineExactlyOnce(result, "mycounter_total", "counter");
        assertHasHelpLineExactlyOnce(result, "mycounter_total", "awesome");

        assertHasValueLineExactlyOnce(result, "mycounter_total", "10.0", blueTag);
        assertHasValueLineExactlyOnce(result, "mycounter_total", "20.0", greenTag);

    }

    @Test
    public void exportGauges() {

        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Metadata metadata = Metadata.builder().withName("mygauge").withDescription("awesome").build();
        Tag blueTag = new Tag("color", "blue");
        registry.gauge(metadata, () -> 42L, blueTag);
        Tag greenTag = new Tag("color", "green");
        registry.gauge(metadata, () -> 26L, greenTag);

        String result = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "mygauge").toString();

        assertHasTypeLineExactlyOnce(result, "mygauge", "gauge");
        assertHasHelpLineExactlyOnce(result, "mygauge", "awesome");

        assertHasValueLineExactlyOnce(result, "mygauge", "42.0", blueTag);
        assertHasValueLineExactlyOnce(result, "mygauge", "26.0", greenTag);
    }

    @Test
    public void exportTimers() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();

        Metadata metadata = Metadata.builder().withName("mytimer").withDescription("awesome").build();
        Tag blueTag = new Tag("color", "blue");
        Timer blueTimer = registry.timer(metadata, blueTag);
        Tag greenTag = new Tag("color", "green");
        Timer greenTimer = registry.timer(metadata, greenTag);

        Tag scopeTag = new Tag("mp_scope", "application");

        blueTimer.update(Duration.ofSeconds(3));
        blueTimer.update(Duration.ofSeconds(4));
        greenTimer.update(Duration.ofSeconds(5));
        greenTimer.update(Duration.ofSeconds(6));

        String result = exporter.exportMetricsByName(MetricRegistry.APPLICATION_SCOPE, "mytimer").toString();

        assertHasTypeLineExactlyOnce(result, "mytimer_seconds", "summary");
        assertHasHelpLineExactlyOnce(result, "mytimer_seconds", "awesome");

        assertHasValueLineExactlyOnce(result, "mytimer_seconds_sum", "7.0", scopeTag, blueTag);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds_sum", "11.0", scopeTag, greenTag);

        assertHasValueLineExactlyOnce(result, "mytimer_seconds_count", "2.0", scopeTag, blueTag);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds_count", "2.0", scopeTag, greenTag);

        assertHasTypeLineExactlyOnce(result, "mytimer_seconds_max", "gauge");
        assertHasValueLineExactlyOnce(result, "mytimer_seconds_max", "4.0", scopeTag, blueTag);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds_max", "6.0", scopeTag, greenTag);

        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "3.0", scopeTag, blueTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "4.0", scopeTag, blueTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "4.0", scopeTag, blueTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "4.0", scopeTag, blueTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "4.0", scopeTag, blueTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "4.0", scopeTag, blueTag, QUANTILE_0_999);

        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "5.0", scopeTag, greenTag, QUANTILE_0_5);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "6.0", scopeTag, greenTag, QUANTILE_0_75);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "6.0", scopeTag, greenTag, QUANTILE_0_95);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "6.0", scopeTag, greenTag, QUANTILE_0_98);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "6.0", scopeTag, greenTag, QUANTILE_0_99);
        assertHasValueLineExactlyOnce(result, "mytimer_seconds", "6.0", scopeTag, greenTag, QUANTILE_0_999);
    }

    /**
     * Test the cases where OpenMetrics keys are different from metric names. For
     * example, with a counter named gc.time, the dot will be converted to an
     * underscore. This is mainly to make sure that we don't accidentally log the
     * HELP and TYPE lines multiple times if there are multiple metrics under such
     * name.
     */
    @Test
    public void testMetricsWhereKeysAreDifferentFromNames() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();

        Metadata metadata = Metadata.builder().withName("metric.a").withDescription("Great").build();
        Tag tag1 = new Tag("tag1", "value1");
        Tag tag2 = new Tag("tag1", "value2");
        registry.counter(metadata, tag1);
        registry.counter(metadata, tag2);

        String result = exporter.exportAllScopes().toString();

        assertHasHelpLineExactlyOnce(result, "metric_a_total", "Great");
        assertHasTypeLineExactlyOnce(result, "metric_a_total", "counter");
    }

    @Test
    public void testNewlineCharacterEscaping() {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);
        PrometheusMetricsExporter exporter = new PrometheusMetricsExporter();
        Tag tag = new Tag("a", "b\nc");
        registry.counter("mycounter", tag);

        String result = exporter.exportOneScope(MetricRegistry.APPLICATION_SCOPE).toString();

        assertTrue(result.contains("{a=\"b\\nc\""));
    }

    private void assertHasValueLineExactlyOnce(String output, String key, String value, Tag... tags) {
        List<String> foundLines = getLines(output, key, value, tags);
        if (foundLines.isEmpty())
            Assert.fail("Couldn't find a line with key=" + key + ", value=" + value + ", tags=" + Arrays.toString(tags)
                    + " in the OpenMetrics output: \n" + output);
        if (foundLines.size() > 1)
            Assert.fail("Found key=" + key + ", value=" + value + ", tags=" + Arrays.toString(tags)
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    private void assertHasTypeLineExactlyOnce(String output, String key, String type) {
        List<String> foundLines = getLinesByRegex(output, "# TYPE " + quote(key) + " " + quote(type));
        if (foundLines.isEmpty())
            Assert.fail("Couldn't find a TYPE line with key=" + key + " and type=" + type
                    + " in the OpenMetrics output: \n" + output);
        if (foundLines.size() > 1)
            Assert.fail("Found TYPE line with key=" + key + " and type=" + type
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    private void assertHasHelpLineExactlyOnce(String output, String key, String help) {
        List<String> foundLines = getLinesByRegex(output, "# HELP " + quote(key) + " " + quote(help));
        if (foundLines.isEmpty())
            Assert.fail("Couldn't find a HELP line with key=" + key + " and help=" + help
                    + " in the OpenMetrics output: \n" + output);
        if (foundLines.size() > 1)
            Assert.fail("Found HELP line with key=" + key + " and help=" + help
                    + " in the OpenMetrics output more than once! Output: \n" + output);
    }

    /**
     * get a line from an Prometheus output which contains a metric with the
     * specified name, approximate value and tags (order of the tags doesn't matter)
     * 
     * With the value, will match with tolerance to 5% of the expected value
     */
    private List<String> getLines(String output, String metricName, String value, Tag... tags) {

        return Arrays.stream(output.split("\n"))
                // filter by metric name at the beginning of the line
                .filter(line -> line.matches(quote(metricName) + "\\{.+"))
                // filter by metric value at the end of the line
                .filter(line -> {
                    if (value.equals("*"))
                        return true;
                    double outputVal = Double.valueOf(line.split("}")[1].trim());
                    if (equalsWithTolerance(Double.valueOf(value), outputVal, (Double.valueOf(value) * 0.05))) {
                        return true;
                    }
                    return false;
                })
                // filter by present tags
                .filter(line -> {
                    boolean matches = true;
                    for (Tag tag : tags) {
                        if (!line.matches(".+" + quote(tag.getTagName()) + "=\"" + quote(tag.getTagValue()) + "\".+")) {
                            matches = false;
                        }

                    }
                    return matches;
                }).collect(Collectors.toList());
    }

    private List<String> getLinesByRegex(String output, String regex) {
        return Arrays.stream(output.split("\n")).filter(line -> line.matches(regex)).collect(Collectors.toList());
    }

    private boolean equalsWithTolerance(double expected, double actual, double delta) {
        if (Double.compare(expected, actual) == 0) {
            return true;
        }
        if ((Math.abs(expected - actual) <= delta)) {
            return true;
        }

        return false;
    }
}
