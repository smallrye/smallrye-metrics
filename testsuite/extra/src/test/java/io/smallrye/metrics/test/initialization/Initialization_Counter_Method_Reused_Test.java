package io.smallrye.metrics.test.initialization;

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
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.test.MeterRegistrySetup;

@RunWith(Arquillian.class)
public class Initialization_Counter_Method_Reused_Test extends MeterRegistrySetup {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebResource(new StringAsset("smallrye.metrics.append-scope-tags=false"),
                        "WEB-INF/classes/META-INF/microprofile-config.properties")
                .addClasses(BeanWithCounter_Method_Reused.class);
    }

    @Inject
    private MetricRegistry registry;

    @Inject
    private BeanWithCounter_Method_Reused bean;

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

    public static class BeanWithCounter_Method_Reused {

        @Counted(name = "counter_method", absolute = true)
        public void counterMethod() {

        }

        @Counted(name = "counter_method", absolute = true)
        public void counterMethod2() {

        }

        @PermitAll
        public void irrelevantAnnotatedMethod() {

        }

    }

}
