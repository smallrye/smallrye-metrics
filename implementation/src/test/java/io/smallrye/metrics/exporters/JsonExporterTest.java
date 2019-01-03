package io.smallrye.metrics.exporters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.MeterImpl;

public class JsonExporterTest {

    @Test
    public void testExportOfDifferentMeterImplementations() {

        final String LINE_SEPARATOR = "\n";

        JsonExporter exporter = new JsonExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        // the export should behave identical for any class derived from Meter
        Meter[] meters = { new MeterImpl(), new SomeMeter() };
        int idx = 0;
        for (Meter m : meters) {
            String name = "json_meter_" + idx++;
            applicationRegistry.register(name, m);
            StringBuffer out = exporter.exportOneMetric(MetricRegistry.Type.APPLICATION, name);
            assertNotNull(out);
            List<String> lines = Arrays.asList(out.toString().split(LINE_SEPARATOR));
            assertEquals(1, lines.stream().filter(line -> line.contains("\"" + name + "\"")).count());
            assertEquals(1, lines.stream().filter(line -> line.contains("\"count\": 0")).count());
        }
    }
}