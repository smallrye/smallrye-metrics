/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.smallrye.metrics.registration;

import java.io.Serializable;
import java.util.concurrent.Callable;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

/**
 * Test for inferring metric type in various corner cases of metric registration
 */
public class RegistrationCornerCasesTest {

    private final MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanupApplicationMetrics() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Test
    public void lambdaExpression() {
        registry.register("test", (Gauge<Long>) () -> 42L);

        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(42L, registry.getGauge(new MetricID("test")).getValue());
    }

    @Test
    public void lambdaExpressionCastToMultipleInterfaces() {
        registry.register("test", (DummyInterface & Gauge<Long>) () -> 18L);

        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(18L, registry.getGauge(new MetricID("test")).getValue());
    }

    @Test
    public void specialClass() {
        registry.register("test", new LambdaGauge(() -> 42L));

        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(42L, registry.getGauge(new MetricID("test")).getValue());
    }

    @Test
    public void specialClassImplementingMultipleInterfaces() {
        registry.register("test", new Bar(() -> 42L));

        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(42L, registry.getGauge(new MetricID("test")).getValue());
    }

    @Test
    public void lambdaCastToInterfaceThatExtendsGauge() {
        registry.register("test", (MySpecialThing) (() -> 42L));

        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(42L, registry.getGauge(new MetricID("test")).getValue());
    }

    @Test
    public void classThatImplementsMetricViaSuperClass() {
        registry.register("test", new Baz());

        Assert.assertEquals(MetricType.COUNTER, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(13L, registry.getCounter(new MetricID("test")).getCount());
    }

    @Test
    public void ambiguousClass() {
        try {
            // the Ambiguous class can represent a counter as well as a gauge; if we don't provide the type
            // during registration, the metric registry will try to infer it, which should fail
            registry.register("test", new Ambiguous());
            Assert.fail("Should fail due to ambiguity");
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void ambiguousClassButTypeIsProvidedDuringRegistration() {
        Metadata metadata = Metadata.builder()
                .withType(MetricType.COUNTER)
                .withName("test")
                .build();
        // the Ambiguous class can be a counter as well as gauge, but we specified the type in the metadata
        // so it should be registered as a counter
        registry.register(metadata, new Ambiguous());

        Assert.assertEquals(MetricType.COUNTER, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(666L, registry.getCounter(new MetricID("test")).getCount());
    }

    @Test
    public void ambiguousClassButTypeIsProvidedDuringRegistrationWithTags() {
        Metadata metadata = Metadata.builder()
                .withType(MetricType.COUNTER)
                .withName("test")
                .build();
        // the Ambiguous class can be a counter as well as gauge, but we specified the type in the metadata
        // so it should be registered as a counter
        registry.register(metadata, new Ambiguous(), new Tag("a", "b"));

        Assert.assertEquals(MetricType.COUNTER, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(666L, registry.getCounter(new MetricID("test", new Tag("a", "b"))).getCount());
    }

    @Test
    public void anonymousClassThatImplementsMetricViaSuperClass() {
        registry.register("test", new Bax() {
            @Override
            public long getCount() {
                return 145;
            }
        });

        Assert.assertEquals(MetricType.COUNTER, registry.getMetadata("test").getTypeRaw());
        Assert.assertEquals(145, registry.getCounter(new MetricID("test")).getCount());
    }

    @Test
    public void anonymousCounter() {
        registry.register("test", new Counter() {
            @Override
            public void inc() {
            }

            @Override
            public void inc(long l) {
            }

            @Override
            public long getCount() {
                return 3;
            }
        });
        Assert.assertEquals(3L, registry.getCounter(new MetricID("test")).getCount());
    }

    @Test
    public void lambdaGaugeInRegisterMethod() {
        registry.register(Metadata.builder().withName("test").build(), (Gauge<Long>) () -> 1L);
        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
    }

    @Test
    public void lambdaGaugeInRegisterMethodWithTags() {
        registry.register(Metadata.builder().withName("test").build(), (Gauge<Long>) () -> 1L, new Tag("a", "b"));
        Assert.assertEquals(MetricType.GAUGE, registry.getMetadata("test").getTypeRaw());
    }

    interface DummyInterface {

    }

    interface MySpecialThing extends Gauge<Long> {

    }

    static class LambdaGauge implements Gauge<Number> {

        public LambdaGauge(Callable<Number> callable) {
            this.callable = callable;
        }

        @Override
        public Number getValue() {
            try {
                return this.callable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        private final Callable<Number> callable;
    }

    static class Bar implements Serializable, Gauge<Number> {

        public Bar(Callable<Number> callable) {
            this.callable = callable;
        }

        @Override
        public Number getValue() {
            try {
                return this.callable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        private final Callable<Number> callable;
    }

    abstract static class Bax extends ICanCount {

        @Override
        public void inc() {
        }

        @Override
        public void inc(long n) {
        }

    }

    static class Baz extends ICanCount {

        @Override
        public void inc() {
        }

        @Override
        public void inc(long n) {
        }

        @Override
        public long getCount() {
            return 13L;
        }
    }

    abstract static class ICanCount implements Counter {

    }

    static class Ambiguous implements Counter, Gauge<Long> {

        @Override
        public void inc() {
        }

        @Override
        public void inc(long n) {
        }

        @Override
        public long getCount() {
            return 666;
        }

        @Override
        public Long getValue() {
            return 321L;
        }
    }

}
