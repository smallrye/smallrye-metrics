package org.wildfly.swarm.microprofile.metrics.reusability;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

/**
 * Test that two metrics of the same name and differing tags can be created by annotations.
 */
@RunWith(Arquillian.class)
public class NonReusableMetricWithDifferingTagsTest {

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private NonReusableMetricWithDifferingTagsBean bean;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(NonReusableMetricWithDifferingTagsBean.class);
    }

    @Test
    public void test() {
        bean.colorBlue();
        bean.colorBlue();
        bean.colorRed();
        Assert.assertEquals(2,
                metricRegistry.getCounters().get(new MetricID("colorCounter", new Tag("color", "blue"))).getCount());
        Assert.assertEquals(1,
                metricRegistry.getCounters().get(new MetricID("colorCounter", new Tag("color", "red"))).getCount());
    }

}
