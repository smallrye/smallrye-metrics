package io.smallrye.metrics.test.initialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class Initialization_Counter_Constructor_Test {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(BeanWithCounter_Constructor.class);
    }

    @Inject
    MetricRegistry registry;

    @Test
    public void test() {
        Counter metricFromConstructor = registry.getCounters()
                .get(new MetricID(
                        "io.smallrye.metrics.test.initialization.Initialization_Counter_Constructor_Test" +
                                "$BeanWithCounter_Constructor.BeanWithCounter_Constructor"));
        assertNotNull(metricFromConstructor);
        assertEquals(1, registry.getCounters().size());
    }

    private static class BeanWithCounter_Constructor {

        @Counted
        public BeanWithCounter_Constructor() {

        }

    }

}
