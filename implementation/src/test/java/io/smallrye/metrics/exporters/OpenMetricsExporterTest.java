package io.smallrye.metrics.exporters;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;
import io.smallrye.metrics.app.MeterImpl;
import io.smallrye.metrics.app.TimerImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;
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

import static io.smallrye.metrics.exporters.OpenMetricsExporter.getOpenMetricsMetricName;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class OpenMetricsExporterTest {

    private static final String LINE_SEPARATOR = "\n";

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
        assertEquals("frag3", getOpenMetricsMetricName("FRAG3"));
        assertEquals("unicast3", getOpenMetricsMetricName("UNICAST3"));

        assertEquals("foo_bar", getOpenMetricsMetricName("FOO-BAR"));
        assertEquals("foo_bar", getOpenMetricsMetricName("FooBAR"));
        assertEquals("foo_bar", getOpenMetricsMetricName("FooBar"));
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
            Assert.assertThat(out, containsString(expectedLine));
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
            Assert.assertThat(out, containsString(expectedLine));
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
            Assert.assertThat(out, containsString(expectedLine));
        }
    }


    /**
     * In OpenMetrics exporter, if the metric name does not end with _total, then _total should be appended automatically.
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
        Assert.assertThat(export, containsString("application_counter1_total{a=\"b\"}"));

        // in this case _total should NOT be appended
        registry.counter("counter2_total", tag);
        export = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, new MetricID("counter2_total", tag)).toString();
        Assert.assertThat(export, containsString("application_counter2_total{a=\"b\"}"));

    }
}
