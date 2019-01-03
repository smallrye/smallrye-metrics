package io.smallrye.metrics.exporters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, name);
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, name);
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
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, name);
            assertNotNull(out);
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }
}