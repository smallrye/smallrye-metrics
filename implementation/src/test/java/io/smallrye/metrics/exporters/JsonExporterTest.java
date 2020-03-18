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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.StringReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.TimerImpl;

public class JsonExporterTest {

    private static final String LINE_SEPARATOR = "\n";

    @After
    public void cleanupApplicationMetrics() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
        MetricRegistries.get(MetricRegistry.Type.BASE).removeMatching(MetricFilter.ALL);
        MetricRegistries.get(MetricRegistry.Type.VENDOR).removeMatching(MetricFilter.ALL);
    }

    @Test
    public void testExportOfDifferentMeterImplementations() {

        JsonExporter exporter = new JsonExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Meter
        Meter[] meters = { new MeterImpl(), new SomeMeter() };
        int idx = 0;
        for (Meter m : meters) {
            String name = "meter_" + idx++;
            applicationRegistry.register(name, m);
            StringBuilder out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
            assertNotNull(out);
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }

    @Test
    public void testExportOfDifferentHistogramImplementations() {

        JsonExporter exporter = new JsonExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Histogram
        Histogram[] histograms = { new HistogramImpl(new ExponentiallyDecayingReservoir()), new SomeHistogram() };
        int idx = 0;
        for (Histogram h : histograms) {
            String name = "histo_" + idx++;
            applicationRegistry.register(name, h);
            StringBuilder out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
            assertNotNull(out);
            System.out.println(out.toString());
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }

    @Test
    public void testExportOfDifferentTimerImplementations() {

        JsonExporter exporter = new JsonExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Timer
        Timer[] timers = { new TimerImpl(), new SomeTimer() };
        int idx = 0;
        for (Timer t : timers) {
            String name = "json_timer_" + idx++;
            applicationRegistry.register(name, t);
            StringBuilder out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
            assertNotNull(out);
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }

    @Test
    public void testGauges() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Gauge<Long> gaugeWithoutTags = () -> 1L;
        Gauge<Long> gaugeRed = () -> 2L;
        Gauge<Long> gaugeBlue = () -> 3L;

        final Metadata metadata = new MetadataBuilder()
                .withType(MetricType.GAUGE)
                .withName("mygauge")
                .build();

        registry.register(metadata, gaugeWithoutTags);
        registry.register(metadata, gaugeRed, new Tag("color", "red"));
        registry.register(metadata, gaugeBlue, new Tag("color", "blue"), new Tag("foo", "bar"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mygauge").toString();
        System.out.println(result);
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals(1, json.getInt("mygauge"));
        assertEquals(2, json.getInt("mygauge;color=red"));
        assertEquals(3, json.getInt("mygauge;color=blue;foo=bar"));
    }

    @Test
    public void testCounters() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Counter counterWithoutTags = registry.counter("mycounter");
        Counter counterRed = registry.counter("mycounter", new Tag("color", "red"));
        Counter counterBlue = registry.counter("mycounter", new Tag("color", "blue"), new Tag("foo", "bar"));

        counterWithoutTags.inc(1);
        counterRed.inc(2);
        counterBlue.inc(3);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mycounter").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals(1, json.getInt("mycounter"));
        assertEquals(2, json.getInt("mycounter;color=red"));
        assertEquals(3, json.getInt("mycounter;color=blue;foo=bar"));
    }

    @Test
    public void testConcurrentGauges() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        ConcurrentGauge cGaugeWithoutTags = registry.concurrentGauge("mycgauge");
        ConcurrentGauge cGaugeRed = registry.concurrentGauge("mycgauge", new Tag("color", "red"));
        ConcurrentGauge cGaugeBlue = registry.concurrentGauge("mycgauge", new Tag("color", "blue"), new Tag("foo", "bar"));

        cGaugeWithoutTags.inc();
        cGaugeRed.inc();
        cGaugeRed.inc();
        cGaugeBlue.inc();
        cGaugeBlue.inc();
        cGaugeBlue.inc();

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mycgauge").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonObject myCgaugeObject = json.getJsonObject("mycgauge");

        assertEquals(1, myCgaugeObject.getInt("current"));
        assertEquals(2, myCgaugeObject.getInt("current;color=red"));
        assertEquals(3, myCgaugeObject.getInt("current;color=blue;foo=bar"));

        assertNotNull(myCgaugeObject.getJsonNumber("min"));
        assertNotNull(myCgaugeObject.getJsonNumber("min;color=red"));
        assertNotNull(myCgaugeObject.getJsonNumber("min;color=blue;foo=bar"));

        assertNotNull(myCgaugeObject.getJsonNumber("max"));
        assertNotNull(myCgaugeObject.getJsonNumber("max;color=red"));
        assertNotNull(myCgaugeObject.getJsonNumber("max;color=blue;foo=bar"));
    }

    @Test
    public void testMeters() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Meter meterWithoutTags = registry.meter("mymeter");
        Meter meterRed = registry.meter("mymeter", new Tag("color", "red"));
        Meter meterBlue = registry.meter("mymeter", new Tag("color", "blue"), new Tag("foo", "bar"));

        meterWithoutTags.mark(1);
        meterRed.mark(2);
        meterBlue.mark(3);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mymeter").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonObject myMeterObject = json.getJsonObject("mymeter");
        assertEquals(1, myMeterObject.getInt("count"));
        assertEquals(2, myMeterObject.getInt("count;color=red"));
        assertEquals(3, myMeterObject.getInt("count;color=blue;foo=bar"));

        assertNotNull(myMeterObject.getJsonNumber("meanRate"));
        assertNotNull(myMeterObject.getJsonNumber("meanRate;color=red"));
        assertNotNull(myMeterObject.getJsonNumber("meanRate;color=blue;foo=bar"));

        assertNotNull(myMeterObject.getJsonNumber("oneMinRate"));
        assertNotNull(myMeterObject.getJsonNumber("oneMinRate;color=red"));
        assertNotNull(myMeterObject.getJsonNumber("oneMinRate;color=blue;foo=bar"));

        assertNotNull(myMeterObject.getJsonNumber("fiveMinRate"));
        assertNotNull(myMeterObject.getJsonNumber("fiveMinRate;color=red"));
        assertNotNull(myMeterObject.getJsonNumber("fiveMinRate;color=blue;foo=bar"));

        assertNotNull(myMeterObject.getJsonNumber("fifteenMinRate"));
        assertNotNull(myMeterObject.getJsonNumber("fifteenMinRate;color=red"));
        assertNotNull(myMeterObject.getJsonNumber("fifteenMinRate;color=blue;foo=bar"));
    }

    @Test
    public void testHistograms() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Histogram histogramWithoutTags = registry.histogram("myhistogram");
        Histogram histogramRed = registry.histogram("myhistogram", new Tag("color", "red"));
        Histogram histogramBlue = registry.histogram("myhistogram", new Tag("color", "blue"), new Tag("foo", "bar"));

        histogramWithoutTags.update(1);
        histogramRed.update(2);
        histogramBlue.update(3);

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "myhistogram").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonObject myhistogramObject = json.getJsonObject("myhistogram");

        assertEquals(1.0, myhistogramObject.getJsonNumber("count").doubleValue(), 1e-10);
        assertEquals(1.0, myhistogramObject.getJsonNumber("count;color=red").doubleValue(), 1e-10);
        assertEquals(1.0, myhistogramObject.getJsonNumber("count;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p50").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p50;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p50;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p75").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p75;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p75;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p95").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p95;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p95;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p98").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p98;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p98;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p99").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p99;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p99;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("p999").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("p999;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("p999;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("min").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("min;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("min;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("mean").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("mean;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("mean;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, myhistogramObject.getJsonNumber("max").doubleValue(), 1e-10);
        assertEquals(2.0, myhistogramObject.getJsonNumber("max;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, myhistogramObject.getJsonNumber("max;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(0.0, myhistogramObject.getJsonNumber("stddev").doubleValue(), 1e-10);
        assertEquals(0.0, myhistogramObject.getJsonNumber("stddev;color=red").doubleValue(), 1e-10);
        assertEquals(0.0, myhistogramObject.getJsonNumber("stddev;color=blue;foo=bar").doubleValue(), 1e-10);
    }

    @Test
    public void testSimpleTimers() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new MetadataBuilder()
                .withUnit(MetricUnits.SECONDS)
                .withName("mysimpletimer")
                .build();

        SimpleTimer timerWithoutTags = registry.simpleTimer(metadata);
        SimpleTimer timerRed = registry.simpleTimer(metadata, new Tag("color", "red"));
        SimpleTimer timerBlue = registry.simpleTimer(metadata, new Tag("color", "blue"), new Tag("foo", "bar"));

        timerWithoutTags.update(Duration.ofSeconds(1));
        timerRed.update(Duration.ofSeconds(2));
        timerBlue.update(Duration.ofSeconds(3));
        timerBlue.update(Duration.ofSeconds(4));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mysimpletimer").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonObject mytimerObject = json.getJsonObject("mysimpletimer");

        assertEquals(1.0, mytimerObject.getJsonNumber("count").doubleValue(), 1e-10);
        assertEquals(1.0, mytimerObject.getJsonNumber("count;color=red").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("count;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("elapsedTime").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("elapsedTime;color=red").doubleValue(), 1e-10);
        assertEquals(7.0, mytimerObject.getJsonNumber("elapsedTime;color=blue;foo=bar").doubleValue(), 1e-10);
    }

    @Test
    public void testTimers() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new MetadataBuilder()
                .withUnit(MetricUnits.SECONDS)
                .withName("mytimer")
                .build();

        Timer timerWithoutTags = registry.timer(metadata);
        Timer timerRed = registry.timer(metadata, new Tag("color", "red"));
        Timer timerBlue = registry.timer(metadata, new Tag("color", "blue"), new Tag("foo", "bar"));

        timerWithoutTags.update(Duration.ofSeconds(1));
        timerRed.update(Duration.ofSeconds(2));
        timerBlue.update(Duration.ofSeconds(3));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "mytimer").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonObject mytimerObject = json.getJsonObject("mytimer");

        assertEquals(1.0, mytimerObject.getJsonNumber("count").doubleValue(), 1e-10);
        assertEquals(1.0, mytimerObject.getJsonNumber("count;color=red").doubleValue(), 1e-10);
        assertEquals(1.0, mytimerObject.getJsonNumber("count;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("elapsedTime").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("elapsedTime;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("elapsedTime;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p50").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p50;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p50;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p75").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p75;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p75;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p95").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p95;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p95;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p98").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p98;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p98;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p99").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p99;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p99;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("p999").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("p999;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("p999;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("min").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("min;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("min;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("mean").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("mean;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("mean;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(1.0, mytimerObject.getJsonNumber("max").doubleValue(), 1e-10);
        assertEquals(2.0, mytimerObject.getJsonNumber("max;color=red").doubleValue(), 1e-10);
        assertEquals(3.0, mytimerObject.getJsonNumber("max;color=blue;foo=bar").doubleValue(), 1e-10);

        assertEquals(0.0, mytimerObject.getJsonNumber("stddev").doubleValue(), 1e-10);
        assertEquals(0.0, mytimerObject.getJsonNumber("stddev;color=red").doubleValue(), 1e-10);
        assertEquals(0.0, mytimerObject.getJsonNumber("stddev;color=blue;foo=bar").doubleValue(), 1e-10);
    }

    @Test
    public void testSemicolonInTagValue() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        registry.counter("counter1",
                new Tag("tag1", "i;have;semicolons"),
                new Tag("tag2", "i;have;semicolons;as;well"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertNotNull("Semicolons in tag values should be converted to underscores",
                json.getJsonNumber("counter1;tag1=i_have_semicolons;tag2=i_have_semicolons_as_well"));
    }

    @Test
    public void testDoubleQuotesInTagValue() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        registry.counter("counter1",
                new Tag("tag1", "i_have\"quotes\""));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals("Double quotes in tag values should be escaped", "counter1;tag1=i_have\"quotes\"",
                json.keySet().stream().findFirst().get());
    }

    @Test
    public void testNewlineCharacterInTagValue() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        registry.counter("counter1",
                new Tag("tag1", "i_have\n_two_lines"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals("Newline chars in tag values should be escaped as \\n",
                "counter1;tag1=i_have\n_two_lines", json.keySet().stream().findFirst().get());
    }

    @Test
    public void testAllScopes() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).counter("c1");
        MetricRegistries.get(MetricRegistry.Type.BASE).counter("b1");
        MetricRegistries.get(MetricRegistry.Type.VENDOR).counter("a1");

        String result = exporter.exportAllScopes().toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals(0, json.getJsonObject("application").getInt("c1"));
        assertEquals(0, json.getJsonObject("base").getInt("b1"));
        assertEquals(0, json.getJsonObject("vendor").getInt("a1"));
    }

    @Test
    public void testOneScope() {
        JsonExporter exporter = new JsonExporter();
        MetricRegistries.get(MetricRegistry.Type.VENDOR).counter("c1");

        String result = exporter.exportOneScope(MetricRegistry.Type.VENDOR).toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        assertEquals(0, json.getInt("c1"));
    }

}
