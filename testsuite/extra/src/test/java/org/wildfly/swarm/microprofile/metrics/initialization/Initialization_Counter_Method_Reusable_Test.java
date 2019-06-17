package org.wildfly.swarm.microprofile.metrics.initialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;

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
public class Initialization_Counter_Method_Reusable_Test {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addClasses(BeanWithCounter_Method_Reusable.class);
    }

    @Inject
    private MetricRegistry registry;

    @Inject
    private BeanWithCounter_Method_Reusable bean;

    @Test
    public void test() {
        assertTrue(registry.getCounters().containsKey(new MetricID("counter_method")));
        assertTrue(registry.getCounters((metricID, metric) -> metricID.getName().contains("irrelevant")).isEmpty());
        bean.counterMethod();
        assertEquals(1, registry.getCounters().get(new MetricID("counter_method")).getCount());
        bean.counterMethod2();
        assertEquals(2, registry.getCounters().get(new MetricID("counter_method")).getCount());
        assertEquals(1, registry.getCounters().size());
    }

    public static class BeanWithCounter_Method_Reusable {

        @Counted(name = "counter_method", absolute = true, reusable = true)
        public void counterMethod() {

        }

        @Counted(name = "counter_method", absolute = true, reusable = true)
        public void counterMethod2() {

        }

        @PermitAll
        public void irrelevantAnnotatedMethod() {

        }

    }

}
