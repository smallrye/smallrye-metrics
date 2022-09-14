package io.smallrye.metrics.test.initialization;

import static org.junit.Assert.assertTrue;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.smallrye.metrics.test.MeterRegistrySetup;

@RunWith(Arquillian.class)
public class Initialization_Counter_Method_Test extends MeterRegistrySetup {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebResource(new StringAsset("smallrye.metrics.append-scope-tags=false"),
                        "WEB-INF/classes/META-INF/microprofile-config.properties")
                .addClasses(BeanWithCounter_Method.class);
    }

    @Inject
    MetricRegistry registry;

    @Inject
    BeanWithCounter_Method bean;

    @Test
    public void test() {
        assertTrue(registry.getCounters().containsKey(new MetricID("counter_method")));
        assertTrue(registry.getCounters((metricID, metric) -> metricID.getName().contains("irrelevant")).isEmpty());
        bean.counterMethod();
        Assert.assertEquals(1, registry.getCounters().get(new MetricID("counter_method")).getCount());
    }

    @ApplicationScoped
    public static class BeanWithCounter_Method {

        @Counted(name = "counter_method", absolute = true)
        public void counterMethod() {

        }

        @PermitAll
        public void irrelevantAnnotatedMethod() {

        }

    }

}
