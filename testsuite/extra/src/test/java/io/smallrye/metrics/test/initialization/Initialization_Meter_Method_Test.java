package io.smallrye.metrics.test.initialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.test.MeterRegistrySetup;

@RunWith(Arquillian.class)
public class Initialization_Meter_Method_Test extends MeterRegistrySetup {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebResource(new StringAsset("smallrye.metrics.append-scope-tags=false"),
                        "WEB-INF/classes/META-INF/microprofile-config.properties")
                .addClasses(BeanWithMeter_Method.class);
    }

    @Inject
    MetricRegistry registry;

    @Inject
    BeanWithMeter_Method bean;

    @Test
    public void test() {
        assertTrue(registry.getMeters().containsKey(new MetricID("meter_method")));
        bean.meterMethod();
        assertEquals(1, registry.getMeters().get(new MetricID("meter_method")).getCount());
    }

    public static class BeanWithMeter_Method {

        @Metered(name = "meter_method", absolute = true)
        public void meterMethod() {

        }

    }

}
