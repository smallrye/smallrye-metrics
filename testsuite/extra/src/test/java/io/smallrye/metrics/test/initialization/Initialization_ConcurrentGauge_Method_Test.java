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

import static org.junit.Assert.assertTrue;

import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
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
public class Initialization_ConcurrentGauge_Method_Test extends MeterRegistrySetup {

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebResource(new StringAsset("smallrye.metrics.append-scope-tags=false"),
                        "WEB-INF/classes/META-INF/microprofile-config.properties")
                .addClasses(BeanWithConcurrentGauge_Method.class);
    }

    @Inject
    MetricRegistry registry;

    @Test
    public void test() {
        assertTrue(registry.getConcurrentGauges().containsKey(new MetricID("cgauged_method")));
    }

    public static class BeanWithConcurrentGauge_Method {

        @ConcurrentGauge(name = "cgauged_method", absolute = true)
        public void cGaugedMethod() {

        }

    }

}
