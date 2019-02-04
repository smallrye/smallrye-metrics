package org.wildfly.swarm.microprofile.metrics.inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metric;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

public class NonReusableMetricInjectionBean {

    @Counted(name = "mycounter", absolute = true, tags = {"k=v1"})
    public void counter() {
    }

    @Counted(name = "mycounter", absolute = true, tags = {"k=v2"})
    public void counter2() {
    }


    // each time this bean gets constructed and injected, change the values of metrics to verify that
    // we can inject metrics using an annotated method parameter
    @Inject
    public void increaseCountersOnInjecting(
            @Metric(name = "mycounter", absolute = true, tags = {"k=v1"}) Counter counter1,
            @Metric(name = "mycounter", absolute = true, tags = {"k=v2"}) Counter counter2
    ) {
        counter1.inc(2);
        counter2.inc(3);
    }

}
