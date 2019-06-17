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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.After;
import org.junit.Test;

import io.smallrye.metrics.app.CounterImpl;

public class MetricRegistryThreadSafetyTest {

    private final MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);

    @After
    public void cleanup() {
        registry.removeMatching(MetricFilter.ALL);
    }

    @Test
    public void tryRegisterSameMetricMultipleTimesInParallel() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            cleanup();
            final AtomicReference<Counter> actuallyRegisteredCounter = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(100);
            final ExecutorService executor = Executors.newFixedThreadPool(100);
            final CompletableFuture[] futures = IntStream.range(0, 100)
                    .mapToObj(j -> CompletableFuture.runAsync(
                            () -> {
                                latch.countDown();
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                }
                                CounterImpl counter = registry.register("mycounter", new CounterImpl());
                                actuallyRegisteredCounter.set(counter);
                            }, executor))
                    .toArray(CompletableFuture[]::new);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertEquals("exactly one attempt should go through",
                    1, Arrays.stream(futures).filter(f -> {
                        try {
                            f.get();
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    }).count());

            assertEquals("99 attempts should fail with IllegalArgumentException",
                    99, Arrays.stream(futures).filter(f -> {
                        try {
                            f.get();
                            return false;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return false;
                        } catch (ExecutionException e) {
                            return e.getCause() instanceof IllegalArgumentException;
                        }
                    }).count());

            // verify that the one counter instance that was successfully registered is now indeed in the registry
            assertEquals(actuallyRegisteredCounter.get(), registry.getCounters().get(new MetricID("mycounter")));
        }
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

}
