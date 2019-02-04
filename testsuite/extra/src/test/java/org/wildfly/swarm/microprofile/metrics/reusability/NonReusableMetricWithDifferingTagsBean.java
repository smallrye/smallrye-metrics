package org.wildfly.swarm.microprofile.metrics.reusability;

import org.eclipse.microprofile.metrics.annotation.Counted;

public class NonReusableMetricWithDifferingTagsBean {

    @Counted(name = "colorCounter", absolute = true, tags = {"color=blue"})
    public void colorBlue() {
    }

    @Counted(name = "colorCounter", absolute = true, tags = {"color=red"})
    public void colorRed() {
    }

}
