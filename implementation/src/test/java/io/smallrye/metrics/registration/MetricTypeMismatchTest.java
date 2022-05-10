/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics.registration;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.After;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

public class MetricTypeMismatchTest {

    private MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanupApplicationMetrics() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Test
    public void metricsWithDifferentType() {
        Metadata metadata1 = Metadata.builder().withName("metric1")
                .withDescription("description1").build();
        Metadata metadata2 = Metadata.builder().withName("metric1")
                .withDescription("description2").build();

        registry.histogram(metadata1);
        try {
            registry.timer(metadata2);
            fail("Must not be able to register if a metric with different type is registered under the same name");
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalStateException.class));
            assertEquals(1, registry.getMetrics().size());
        }
    }

    @Test
    public void wrongTypeInMetadata() {
        Metadata metadata1 = Metadata.builder()
                .withName("metric1")
                .withType(MetricType.COUNTER)
                .build();
        try {
            registry.timer(metadata1);
            fail("Must not be able to register a metric if the type in its metadata is different than the what we specified by using a particular registration method.");
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalArgumentException.class));
            assertEquals(0, registry.getMetrics().size());
        }
    }

}
