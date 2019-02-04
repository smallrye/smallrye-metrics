package org.wildfly.swarm.microprofile.metrics.inject;

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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * Test that it is possible to inject a metric using an annotated method parameter.
 */
@RunWith(Arquillian.class)
public class NonReusableMetricInjectionTest {

    @Inject
    private Instance<NonReusableMetricInjectionBean> bean;

    @Inject
    private MetricRegistry metricRegistry;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(NonReusableMetricInjectionBean.class);
    }

    @Test
    public void test() {
        // force creating two instances of the bean
        bean.get();
        bean.get();
        Assert.assertEquals(4, metricRegistry.getCounters().get(new MetricID("mycounter", new Tag("k", "v1"))).getCount());
        Assert.assertEquals(6, metricRegistry.getCounters().get(new MetricID("mycounter", new Tag("k", "v2"))).getCount());
    }

}
