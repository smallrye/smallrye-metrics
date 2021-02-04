package io.smallrye.metrics.test;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Registers a SimpleMeterRegistry for testing purposes, and then removes it after the test is done.
 */
public abstract class MeterRegistrySetup {

    static MeterRegistry registry;

    @BeforeClass
    public static void addRegistries() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterClass
    public static void cleanup() {
        Metrics.removeRegistry(registry);
    }

}
