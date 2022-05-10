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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

public class MetadataMismatchTest {

    private MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanupApplicationMetrics() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Ignore
    // FIXME: this fails because the second registration goes through, I think that should be fixed
    @Test
    public void metricsWithDifferentMetadata() {
        Metadata metadata1 = Metadata.builder().withName("myhistogram").withDescription("description1").build();
        Metadata metadata2 = Metadata.builder().withName("myhistogram").withDescription("description2").build();

        registry.histogram(metadata1);
        try {
            registry.histogram(metadata2);
            fail("Shouldn't be able to re-register a metric with different metadata");
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalStateException.class));
            assertEquals(1, registry.getMetrics().size());
        }
    }

    @Test
    public void reusingMetadata() {
        Metadata metadata1 = Metadata.builder().withName("myhistogram").withDescription("description1").build();

        Histogram histogram1 = registry.histogram(metadata1);
        Histogram histogram2 = registry.histogram("myhistogram", new Tag("color", "blue"));

        assertNotEquals(histogram1, histogram2);
        assertEquals(2, registry.getMetrics().size());
        assertThat(registry.getMetadata().get("myhistogram").description().get(), equalTo("description1"));
    }

    @Test
    public void metricsWithSameTypeAndMetadata() {
        Metadata metadata1 = Metadata.builder().withName("myhistogram").withDescription("description1").build();
        Metadata metadata2 = Metadata.builder().withName("myhistogram").withDescription("description1").build();

        Histogram histogram1 = registry.histogram(metadata1);
        Histogram histogram2 = registry.histogram(metadata2);

        assertEquals(histogram1, histogram2);
        assertEquals(1, registry.getMetrics().size());
    }

}
