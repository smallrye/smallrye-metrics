package org.wildfly.swarm.microprofile.metrics.reusability;

import org.eclipse.microprofile.metrics.annotation.Counted;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NonReusableMetricBean {

    @Counted(name = "countedMethod", absolute = true)
    public void countedMethodOne() {

    }

    @Counted(name = "countedMethod", absolute = true)
    public void countedMethodOne2() {

    }

}
