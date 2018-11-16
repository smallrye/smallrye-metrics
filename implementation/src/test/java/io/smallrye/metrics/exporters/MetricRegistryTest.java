package io.smallrye.metrics.exporters;

import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.app.CounterImpl;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.Test;

public class MetricRegistryTest {

    @Test(expected = IllegalArgumentException.class)
    public void counterMustNotProvideUnit() {
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

        Metric counter = new CounterImpl();
        Metadata metadata = new Metadata("requestCount", MetricType.COUNTER);
        metadata.setUnit(MetricUnits.DAYS);
        registry.register(metadata, counter);
    }
}
