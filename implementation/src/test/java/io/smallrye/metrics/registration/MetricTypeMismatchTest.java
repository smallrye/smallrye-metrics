/*
 * Copyright 2019, 2023 Red Hat, Inc. and/or its affiliates
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

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.metrics.SharedMetricRegistries;

public class MetricTypeMismatchTest {

    private MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricRegistry.APPLICATION_SCOPE);

    @AfterEach
    public void cleanupApplicationMetrics() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Test
    public void metricsWithDifferentType() {
        Metadata metadata1 = Metadata.builder().withName("metric1")
                .withDescription("description1").build();
        Metadata metadata2 = Metadata.builder().withName("metric1")
                .withDescription("description1").build();

        registry.histogram(metadata1);

        Assertions.assertThrows(IllegalStateException.class, () -> registry.timer(metadata2));
        Assertions.assertEquals(1, registry.getMetrics().size());

    }
}
