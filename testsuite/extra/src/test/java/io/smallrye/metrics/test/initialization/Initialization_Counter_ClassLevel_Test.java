/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.smallrye.metrics.test.initialization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
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
public class Initialization_Counter_ClassLevel_Test extends MeterRegistrySetup {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebResource(new StringAsset("smallrye.metrics.append-scope-tags=false"),
                        "WEB-INF/classes/META-INF/microprofile-config.properties")
                .addClasses(BeanWithCounter_ClassLevel.class);
    }

    @Inject
    MetricRegistry registry;

    @Test
    public void test() {
        // metric should be created for the constructor
        assertTrue(
                registry.getCounters().containsKey(new MetricID("customName.BeanWithCounter_ClassLevel", new Tag("t1", "v1"))));

        // metrics should be created for public methods
        assertTrue(registry.getCounters().containsKey(new MetricID("customName.publicMethod", new Tag("t1", "v1"))));
        assertTrue(registry.getCounters().containsKey(new MetricID("customName.publicMethod2", new Tag("t1", "v1"))));

        // but not for private methods
        assertFalse(registry.getCounters().keySet().stream()
                .anyMatch(metricID -> metricID.getName().toLowerCase().contains("private")));
    }

    @Counted(name = "customName", tags = "t1=v1", absolute = true)
    private static class BeanWithCounter_ClassLevel {

        public BeanWithCounter_ClassLevel() {

        }

        public void publicMethod() {

        }

        public void publicMethod2() {

        }

        private void privateMethod() {

        }

    }

}
