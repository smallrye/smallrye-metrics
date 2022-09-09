package io.smallrye.metrics.test.inject;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.test.MeterRegistrySetup;

/**
 * Ensures that the root MeterRegistry can be injected
 */
@RunWith(Arquillian.class)
public class GlobalRegistryInjectionTest extends MeterRegistrySetup {

    @Inject
    private MeterRegistry registry;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClass(NonReusableMetricInjectionBean.class);
    }

    @Test
    public void test() {
        Assert.assertEquals(Metrics.globalRegistry, registry);
    }

}
