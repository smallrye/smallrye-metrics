package io.smallrye.metrics.exporters;

import static io.smallrye.metrics.exporters.PrometheusExporter.getPrometheusMetricName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.Test;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.TimerImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;

public class PrometheusExporterTest {

    private static final String LINE_SEPARATOR = "\n";

    @After
    public void cleanupApplicationMetrics() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    @Test
    public void testUptimeGaugeUnitConversion() {
        PrometheusExporter exporter = new PrometheusExporter();
        MetricRegistry baseRegistry = MetricRegistries.get(MetricRegistry.Type.BASE);

        Gauge gauge = new MGaugeImpl(JmxWorker.instance(), "java.lang:type=Runtime/Uptime");
        Metadata metadata = new ExtendedMetadata("jvm.uptime", "display name", "description", MetricType.GAUGE, "milliseconds");
        baseRegistry.register(metadata, gauge);

        long actualUptime /* in ms */ = ManagementFactory.getRuntimeMXBean().getUptime();
        double actualUptimeInSeconds = actualUptime / 1000.0;

        StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.BASE, "jvm.uptime");
        assertNotNull(out);

        double valueFromPrometheus = -1;
        for (String line : out.toString().split(System.getProperty("line.separator"))) {
            if (line.startsWith("base_jvm_uptime_seconds")) {
                valueFromPrometheus /* in seconds */ = Double.valueOf(line.substring("base:jvm_uptime_seconds".length()).trim());
            }
        }
        assertTrue("Value should not be -1", valueFromPrometheus != -1) ;
        assertTrue(valueFromPrometheus >= actualUptimeInSeconds);
    }

    @Test
    public void metricNameConversion() {
        assertEquals("frag3", getPrometheusMetricName("FRAG3"));
        assertEquals("unicast3", getPrometheusMetricName("UNICAST3"));

        assertEquals("foo_bar", getPrometheusMetricName("FOO-BAR"));
        assertEquals("foo_bar", getPrometheusMetricName("FooBAR"));
        assertEquals("foo_bar", getPrometheusMetricName("FooBar"));
    }

    @Test
    public void testExportOfDifferentMeterImplementations() {

        PrometheusExporter exporter = new PrometheusExporter();
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
            String expectedLine = "application:" + name + "_total 0.0";
            assertEquals(1, lines.stream().filter(line -> line.equals(expectedLine)).count());
        }
    }

    @Test
    public void testExportOfDifferentHistogramImplementations() {

        PrometheusExporter exporter = new PrometheusExporter();
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
            String expectedLine = "application:" + name + "_mean 0.0";
            assertEquals(1, lines.stream().filter(line -> line.equals(expectedLine)).count());
        }
    }

    @Test
    public void testExportOfDifferentTimerImplementations() {

        PrometheusExporter exporter = new PrometheusExporter();
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
            String expectedLine = "application:" + name + "_rate_per_second 0.0";
            assertEquals(1, lines.stream().filter(line -> line.equals(expectedLine)).count());
        }
    }
}
