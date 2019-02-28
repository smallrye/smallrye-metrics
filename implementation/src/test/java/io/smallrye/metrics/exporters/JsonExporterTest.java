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

import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
            assertNotNull(out);
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID(name));
            assertNotNull(out);
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }
}