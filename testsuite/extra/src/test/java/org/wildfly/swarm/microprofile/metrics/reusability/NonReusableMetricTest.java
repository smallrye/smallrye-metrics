package org.wildfly.swarm.microprofile.metrics.reusability;

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
 * Test that if the same metric annotation is repeated and the metric is not marked as reusable, the metric will be rejected.
 */
@RunWith(Arquillian.class)
public class NonReusableMetricTest {

    @Inject
    private NonReusableMetricBean bean;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(NonReusableMetricBean.class);
    }

    // FIXME this test depends on the behavior that annotation-based metrics from ApplicationScoped beans are not registered eagerly
    // but instead they are registered during instantiation of the bean. So if we change that behavior, this test will break.
    // The problem is that if you force the failure eagerly, which causes the whole deployment to fail, I'm not sure how to
    // write a reliable test that can catch a deployment exception
    @Test
    public void test() {
        try {
            // trigger instantiation of the bean
            // this should trigger an error
            bean.countedMethodOne();
            Assert.fail("Metric registration should not go through");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
