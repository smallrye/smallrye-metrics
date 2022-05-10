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

package io.smallrye.metrics;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MetricRegistryThreadSafetyTest {

    private final MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanup() {
        registry.removeMatching(MetricFilter.ALL);
    }

    /**
     * One set of threads is registering new metrics and removing them after a short while,
     * at the same time, another set of threads is retrieving a list of metrics conforming to a filter.
     * None of the threads should be getting any exceptions.
     */
    @Test
    public void tryRegisteringRemovingAndReadingAtTheSameTime() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            cleanup();
            final ExecutorService executor = Executors.newFixedThreadPool(10);
            final CompletableFuture[] futures = IntStream.range(0, 200)
                    .parallel()
                    .mapToObj(j -> CompletableFuture.runAsync(
                            () -> {
                                try {
                                    if (j % 2 == 0) {
                                        MetricID metricID = new MetricID("mycounter", new Tag("number", String.valueOf(j)));
                                        registry.counter("mycounter", new Tag("number", String.valueOf(j)));
                                        registry.remove(metricID);
                                    } else {
                                        registry.getCounters(MetricFilter.ALL);
                                    }
                                } catch (Throwable t) {
                                    t.printStackTrace();
                                    throw t;
                                }
                            }, executor))
                    .toArray(CompletableFuture[]::new);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals("All threads should finish without exceptions",
                    0, Arrays.stream(futures).filter(CompletableFuture::isCompletedExceptionally).count());
        }
    }

    /**
     * Test concurrent calls of MetricRegistryImpl.register() and MetricRegistryImpl.getMetadata()
     * at the same time.
     */
    @Test
    @Ignore // FIXME, fails
    public void registerAndGetMetadata() throws InterruptedException, ExecutionException, TimeoutException {
        final AtomicReference<Throwable> throwableEncounteredDuringTest = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);
        try {
            // to store CompletableFutures for all the operations the test will perform
            CompletableFuture<Void>[] futures = new CompletableFuture[2000];
            for (int i = 0; i < 1000; i++) {
                final int finalI = i;
                futures[2 * i] = CompletableFuture.runAsync(() -> {
                    try {
                        registry.counter("metric" + finalI);
                    } catch (Throwable t) {
                        throwableEncounteredDuringTest.set(t);
                    }

                }, executor);
                futures[2 * i + 1] = CompletableFuture.runAsync(() -> {
                    try {
                        registry.getMetadata();
                    } catch (Throwable t) {
                        throwableEncounteredDuringTest.set(t);
                    }
                }, executor);
            }

            // wait until all tasks finish
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

            // assert that no task received an error and that the registry contains all required data
            Assert.assertNull(throwableEncounteredDuringTest.get());
            Assert.assertEquals(1000, registry.getMetadata().size());
            Assert.assertEquals(1000, registry.getCounters().size());
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

}
