package io.smallrye.metrics.legacyapi;

import org.eclipse.microprofile.metrics.Metric;

import io.micrometer.core.instrument.Meter;

interface MeterHolder extends Metric {
    Meter getMeter();
}
