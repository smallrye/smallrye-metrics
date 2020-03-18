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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

public class ExportersMetricScalingTest {

    @After
    public void cleanup() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    /**
     * Given a Timer with unit=MINUTES,
     * check that the statistics from OpenMetricsExporter will be correctly converted to SECONDS.
     */
    @Test
    public void timer_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("timer1")
                .withType(MetricType.TIMER)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Timer metric = registry.timer(metadata);
        metric.update(Duration.ofHours(1));
        metric.update(Duration.ofHours(2));
        metric.update(Duration.ofHours(3));

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("timer1")).toString();

        Assert.assertThat(exported, containsString("application_timer1_seconds{quantile=\"0.5\"} 7200.0"));
        Assert.assertThat(exported, containsString("application_timer1_mean_seconds 7200.0"));
        Assert.assertThat(exported, containsString("application_timer1_min_seconds 3600.0"));
        Assert.assertThat(exported, containsString("application_timer1_max_seconds 10800.0"));
    }

    /**
     * Given a Timer with unit=MINUTES,
     * check that the statistics from JsonExporter will be presented in MINUTES.
     */
    @Test
    public void timer_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("timer1")
                .withType(MetricType.TIMER)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Timer metric = registry.timer(metadata);
        metric.update(Duration.ofHours(1));
        metric.update(Duration.ofHours(2));
        metric.update(Duration.ofHours(3));

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("timer1")).toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("timer1");
        assertEquals(120.0, json.getJsonNumber("p50").doubleValue(), 0.001);
        assertEquals(120.0, json.getJsonNumber("mean").doubleValue(), 0.001);
        assertEquals(60.0, json.getJsonNumber("min").doubleValue(), 0.001);
        assertEquals(180.0, json.getJsonNumber("max").doubleValue(), 0.001);
        assertEquals(360.0, json.getJsonNumber("elapsedTime").doubleValue(), 0.001);
    }

    /**
     * Given a Histogram with unit=MINUTES,
     * check that the statistics from OpenMetricsExporter will be presented in SECONDS.
     */
    @Test
    public void histogram_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("histogram1")
                .withType(MetricType.HISTOGRAM)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Histogram metric = registry.histogram(metadata);
        metric.update(30);
        metric.update(40);
        metric.update(50);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("histogram1")).toString();

        Assert.assertThat(exported, containsString("application_histogram1_min_seconds 1800.0"));
        Assert.assertThat(exported, containsString("application_histogram1_max_seconds 3000.0"));
        Assert.assertThat(exported, containsString("application_histogram1_mean_seconds 2400.0"));
        Assert.assertThat(exported, containsString("application_histogram1_seconds{quantile=\"0.5\"} 2400.0"));
    }

    /**
     * Given a Histogram with unit=dollars (custom unit),
     * check that the statistics from OpenMetricsExporter will be presented in dollars.
     */
    @Test
    public void histogram_customunit_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("histogram1")
                .withType(MetricType.HISTOGRAM)
                .withUnit("dollars")
                .build();
        Histogram metric = registry.histogram(metadata);
        metric.update(30);
        metric.update(40);
        metric.update(50);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("histogram1")).toString();

        Assert.assertThat(exported, containsString("application_histogram1_min_dollars 30.0"));
        Assert.assertThat(exported, containsString("application_histogram1_max_dollars 50.0"));
        Assert.assertThat(exported, containsString("application_histogram1_mean_dollars 40.0"));
        Assert.assertThat(exported, containsString("application_histogram1_dollars{quantile=\"0.5\"} 40.0"));
    }

    /**
     * Given a Histogram with unit=MINUTES,
     * check that the statistics from JsonExporter will be presented in MINUTES.
     */
    @Test
    public void histogram_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("timer1")
                .withType(MetricType.TIMER)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Timer metric = registry.timer(metadata);
        metric.update(Duration.ofHours(1));
        metric.update(Duration.ofHours(2));
        metric.update(Duration.ofHours(3));

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("timer1")).toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("timer1");
        assertEquals(120.0, json.getJsonNumber("p50").doubleValue(), 0.001);
        assertEquals(120.0, json.getJsonNumber("mean").doubleValue(), 0.001);
        assertEquals(60.0, json.getJsonNumber("min").doubleValue(), 0.001);
        assertEquals(180.0, json.getJsonNumber("max").doubleValue(), 0.001);
    }

    /**
     * Given a Counter,
     * check that the statistics from OpenMetricsExporter will not be scaled in any way.
     */
    @Test
    public void counter_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("counter1")
                .withType(MetricType.COUNTER)
                .build();
        Counter metric = registry.counter(metadata);
        metric.inc(30);
        metric.inc(40);
        metric.inc(50);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter1")).toString();

        Assert.assertThat(exported, containsString("application_counter1_total 120.0"));
    }

    /**
     * Given a Counter,
     * check that the statistics from JsonExporter will not be scaled in any way.
     */
    @Test
    public void counter_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("counter1")
                .withType(MetricType.COUNTER)
                .build();
        Counter metric = registry.counter(metadata);
        metric.inc(10);
        metric.inc(20);
        metric.inc(30);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter1")).toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject();
        assertEquals(60, json.getInt("counter1"));
    }

    /**
     * Given a Meter,
     * check that the statistics from OpenMetrics will be presented as per_second.
     */
    @Test
    public void meter_openMetrics() throws InterruptedException {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("meter1")
                .withType(MetricType.METERED)
                .build();
        Meter metric = registry.meter(metadata);
        metric.mark(10);
        TimeUnit.SECONDS.sleep(1);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("meter1")).toString();

        Assert.assertThat(exported, containsString("application_meter1_total 10.0"));
        double ratePerSecond = Double.parseDouble(Arrays.stream(exported.split("\\n"))
                .filter(line -> line.contains("application_meter1_rate_per_second"))
                .filter(line -> !line.contains("TYPE") && !line.contains("HELP"))
                .findFirst()
                .get()
                .split(" ")[1]);
        Assert.assertTrue("Rate per second should be between 1 and 10 but is " + ratePerSecond,
                ratePerSecond > 1 && ratePerSecond < 10);
    }

    /**
     * Given a Meter,
     * check that the statistics from JsonExporter will be presented as per_second.
     */
    @Test
    public void meter_json() throws InterruptedException {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("meter1")
                .withType(MetricType.METERED)
                .build();
        Meter metric = registry.meter(metadata);
        metric.mark(10);
        TimeUnit.SECONDS.sleep(1);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("meter1")).toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("meter1");
        assertEquals(10, json.getInt("count"));
        double meanRate = json.getJsonNumber("meanRate").doubleValue();
        Assert.assertTrue("meanRate should be between 1 and 10 but is " + meanRate,
                meanRate > 1 && meanRate < 10);
    }

    /**
     * Given a Gauge with unit=MINUTES,
     * check that the statistics from OpenMetricsExporter will be presented in SECONDS.
     */
    @Test
    public void gauge_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("gauge1")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register(metadata, gaugeInstance);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("gauge1")).toString();

        Assert.assertThat(exported, containsString("application_gauge1_seconds 180.0"));
    }

    /**
     * Given a Gauge with unit=dollars (custom unit),
     * check that the statistics from OpenMetricsExporter will be presented in dollars.
     */
    @Test
    public void gauge_customUnit_openMetrics() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("gauge1")
                .withType(MetricType.GAUGE)
                .withUnit("dollars")
                .build();
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register(metadata, gaugeInstance);

        OpenMetricsExporter exporter = new OpenMetricsExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("gauge1")).toString();

        Assert.assertThat(exported, containsString("application_gauge1_dollars 3.0"));
    }

    /**
     * Given a Gauge with unit=MINUTES,
     * check that the statistics from OpenMetricsExporter will be presented in MINUTES.
     */
    @Test
    public void gauge_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = Metadata.builder()
                .withName("gauge1")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.MINUTES)
                .build();
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register(metadata, gaugeInstance);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("gauge1")).toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject();
        assertEquals(3, json.getInt("gauge1"));
    }

}
