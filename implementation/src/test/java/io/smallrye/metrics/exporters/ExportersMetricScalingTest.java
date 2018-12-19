package io.smallrye.metrics.exporters;

import io.smallrye.metrics.MetricRegistries;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

public class ExportersMetricScalingTest {

    @After
    public void cleanup() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    /**
     * Given a Timer with unit=MINUTES,
     * check that the statistics from PrometheusExporter will be correctly converted to SECONDS.
     */
    @Test
    public void timer_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("timer1", MetricType.TIMER, MetricUnits.MINUTES);
        Timer metric = registry.timer(metadata);
        metric.update(1, TimeUnit.HOURS);
        metric.update(2, TimeUnit.HOURS);
        metric.update(3, TimeUnit.HOURS);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "timer1").toString();

        Assert.assertThat(exported, containsString("application:timer1_seconds{quantile=\"0.5\"} 7200.0"));
        Assert.assertThat(exported, containsString("application:timer1_mean_seconds 7200.0"));
        Assert.assertThat(exported, containsString("application:timer1_min_seconds 3600.0"));
        Assert.assertThat(exported, containsString("application:timer1_max_seconds 10800.0"));
    }

    /**
     * Given a Timer with unit=MINUTES,
     * check that the statistics from JsonExporter will be presented in MINUTES.
     */
    @Test
    public void timer_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("timer1", MetricType.TIMER, MetricUnits.MINUTES);
        Timer metric = registry.timer(metadata);
        metric.update(1, TimeUnit.HOURS);
        metric.update(2, TimeUnit.HOURS);
        metric.update(3, TimeUnit.HOURS);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "timer1").toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("timer1");
        assertEquals(120.0, json.getJsonNumber("p50").doubleValue(), 0.001);
        assertEquals(120.0, json.getJsonNumber("mean").doubleValue(), 0.001);
        assertEquals(60.0, json.getJsonNumber("min").doubleValue(), 0.001);
        assertEquals(180.0, json.getJsonNumber("max").doubleValue(), 0.001);
    }

    /**
     * Given a Histogram with unit=MINUTES,
     * check that the statistics from PrometheusExporter will be presented in SECONDS.
     */
    @Test
    public void histogram_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("histogram1", MetricType.HISTOGRAM, MetricUnits.MINUTES);
        Histogram metric = registry.histogram(metadata);
        metric.update(30);
        metric.update(40);
        metric.update(50);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "histogram1").toString();

        Assert.assertThat(exported, containsString("application:histogram1_min_seconds 1800.0"));
        Assert.assertThat(exported, containsString("application:histogram1_max_seconds 3000.0"));
        Assert.assertThat(exported, containsString("application:histogram1_mean_seconds 2400.0"));
        Assert.assertThat(exported, containsString("application:histogram1_seconds{quantile=\"0.5\"} 2400.0"));
    }

    /**
     * Given a Histogram with unit=dollars (custom unit),
     * check that the statistics from PrometheusExporter will be presented in dollars.
     */
    @Test
    public void histogram_customunit_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("histogram1", MetricType.HISTOGRAM, "dollars");
        Histogram metric = registry.histogram(metadata);
        metric.update(30);
        metric.update(40);
        metric.update(50);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "histogram1").toString();

        Assert.assertThat(exported, containsString("application:histogram1_min_dollars 30.0"));
        Assert.assertThat(exported, containsString("application:histogram1_max_dollars 50.0"));
        Assert.assertThat(exported, containsString("application:histogram1_mean_dollars 40.0"));
        Assert.assertThat(exported, containsString("application:histogram1_dollars{quantile=\"0.5\"} 40.0"));
    }

    /**
     * Given a Histogram with unit=MINUTES,
     * check that the statistics from JsonExporter will be presented in MINUTES.
     */
    @Test
    public void histogram_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("timer1", MetricType.TIMER, MetricUnits.MINUTES);
        Timer metric = registry.timer(metadata);
        metric.update(1, TimeUnit.HOURS);
        metric.update(2, TimeUnit.HOURS);
        metric.update(3, TimeUnit.HOURS);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "timer1").toString();


        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("timer1");
        assertEquals(120.0, json.getJsonNumber("p50").doubleValue(), 0.001);
        assertEquals(120.0, json.getJsonNumber("mean").doubleValue(), 0.001);
        assertEquals(60.0, json.getJsonNumber("min").doubleValue(), 0.001);
        assertEquals(180.0, json.getJsonNumber("max").doubleValue(), 0.001);
    }

    /**
     * Given a Counter,
     * check that the statistics from PrometheusExporter will not be scaled in any way.
     */
    @Test
    public void counter_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("counter1", MetricType.COUNTER);
        Counter metric = registry.counter(metadata);
        metric.inc(30);
        metric.inc(40);
        metric.inc(50);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "counter1").toString();

        Assert.assertThat(exported, containsString("application:counter1 120.0"));
    }

    /**
     * Given a Counter,
     * check that the statistics from JsonExporter will not be scaled in any way.
     */
    @Test
    public void counter_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("counter1", MetricType.COUNTER);
        Counter metric = registry.counter(metadata);
        metric.inc(10);
        metric.inc(20);
        metric.inc(30);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "counter1").toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject();
        assertEquals(60, json.getInt("counter1"));
    }

    /**
     * Given a Meter,
     * check that the statistics from PrometheusExporter will be presented as per_second.
     */
    @Test
    public void meter_prometheus() throws InterruptedException {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("meter1", MetricType.METERED);
        Meter metric = registry.meter(metadata);
        metric.mark(10);
        TimeUnit.SECONDS.sleep(1);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "meter1").toString();

        Assert.assertThat(exported, containsString("application:meter1_total 10.0"));
        double ratePerSecond = Double.parseDouble(Arrays.stream(exported.split("\\n"))
                .filter(line -> line.contains("application:meter1_rate_per_second"))
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
        Metadata metadata = new Metadata("meter1", MetricType.METERED);
        Meter metric = registry.meter(metadata);
        metric.mark(10);
        TimeUnit.SECONDS.sleep(1);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "meter1").toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject().getJsonObject("meter1");
        assertEquals(10, json.getInt("count"));
        double meanRate = json.getJsonNumber("meanRate").doubleValue();
        Assert.assertTrue("meanRate should be between 1 and 10 but is " + meanRate,
                meanRate > 1 && meanRate < 10);
    }

    /**
     * Given a Gauge with unit=MINUTES,
     * check that the statistics from PrometheusExporter will be presented in SECONDS.
     */
    @Test
    public void gauge_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("gauge1", MetricType.GAUGE, MetricUnits.MINUTES);
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register("gauge1", gaugeInstance, metadata);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "gauge1").toString();

        Assert.assertThat(exported, containsString("application:gauge1_seconds 180.0"));
    }

    /**
     * Given a Gauge with unit=dollars (custom unit),
     * check that the statistics from PrometheusExporter will be presented in dollars.
     */
    @Test
    public void gauge_customUnit_prometheus() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("gauge1", MetricType.GAUGE, "dollars");
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register("gauge1", gaugeInstance, metadata);

        PrometheusExporter exporter = new PrometheusExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "gauge1").toString();

        Assert.assertThat(exported, containsString("application:gauge1_dollars 3.0"));
    }

    /**
     * Given a Gauge with unit=MINUTES,
     * check that the statistics from PrometheusExporter will be presented in MINUTES.
     */
    @Test
    public void gauge_json() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        Metadata metadata = new Metadata("gauge1", MetricType.GAUGE, MetricUnits.MINUTES);
        Gauge<Long> gaugeInstance = () -> 3L;
        registry.register("gauge1", gaugeInstance, metadata);

        JsonExporter exporter = new JsonExporter();
        String exported = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, "gauge1").toString();

        JsonObject json = Json.createReader(new StringReader(exported)).read().asJsonObject();
        assertEquals(3, json.getInt("gauge1"));
    }

}
