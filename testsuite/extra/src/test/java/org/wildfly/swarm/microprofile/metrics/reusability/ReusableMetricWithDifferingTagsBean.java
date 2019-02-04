package org.wildfly.swarm.microprofile.metrics.reusability;

import org.eclipse.microprofile.metrics.annotation.Counted;

public class ReusableMetricWithDifferingTagsBean {

    @Counted(name = "colorCounter", absolute = true, tags = {"color=blue"}, reusable = true)
    public void colorBlue1() {
    }

    @Counted(name = "colorCounter", absolute = true, tags = {"color=red"}, reusable = true)
    public void colorRed1() {
    }

    @Counted(name = "colorCounter", absolute = true, tags = {"color=blue"}, reusable = true)
    public void colorBlue2() {
    }

    @Counted(name = "colorCounter", absolute = true, tags = {"color=red"}, reusable = true)
    public void colorRed2() {
    }

}
