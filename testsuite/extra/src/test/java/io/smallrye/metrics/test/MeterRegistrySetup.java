package io.smallrye.metrics.test;

import org.junit.BeforeClass;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Registers a SimpleMeterRegistry for testing purposes.
 * TODO: This should probably be automatic (registries should be added by a CDI extension depending on the available
 * registry implementations and appropriate configuration)
 */
public abstract class MeterRegistrySetup {

    static MeterRegistry registry;

    @BeforeClass
    public static void addRegistries() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

}
