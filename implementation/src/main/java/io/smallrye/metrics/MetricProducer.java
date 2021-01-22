package io.smallrye.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;

@ApplicationScoped
public class MetricProducer {

    // TODO

    @Produces
    <T extends Number> Gauge<T> getGauge(InjectionPoint ip) {
        return null;
    }

    @Produces
    Counter getCounter(InjectionPoint ip) {
        return null;
    }

    @Produces
    ConcurrentGauge getConcurrentGauge(InjectionPoint ip) {
        return null;
    }

    @Produces
    Histogram getHistogram(InjectionPoint ip) {
        return null;
    }

    @Produces
    Meter getMeter(InjectionPoint ip) {
        return null;
    }

    @Produces
    Timer getTimer(InjectionPoint ip) {
        return null;
    }

    @Produces
    SimpleTimer getSimpleTimer(InjectionPoint ip) {
        return null;
    }

}
