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

package io.smallrye.metrics.jaxrs;

import java.io.IOException;
import java.time.Duration;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;

import io.smallrye.metrics.MetricRegistries;

/**
 * For explanation, see javadoc of {@link JaxRsMetricsFilter}
 */
public class JaxRsMetricsServletFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            MetricID metricID = (MetricID) servletRequest.getAttribute("smallrye.metrics.jaxrs.metricID");
            if (metricID != null) {
                MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.BASE);
                if (!registry.getMetricIDs().contains(metricID)) {
                    // if no such metric exists yet, register it
                    Metadata metadata = Metadata.builder()
                            .withName(metricID.getName())
                            .withDescription(
                                    "The number of invocations and total response time of this RESTful " +
                                            "resource method since the start of the server.")
                            .withUnit(MetricUnits.NANOSECONDS)
                            .build();
                    registry.simpleTimer(metadata, metricID.getTagsAsArray());
                }

                if (!servletRequest.isAsyncStarted()) {
                    updateMetric(start, metricID);
                } else { // if response is async, update the metric after it really finishes
                    servletRequest.getAsyncContext().addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            updateMetric(start, metricID);
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                            updateMetric(start, metricID);
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                            updateMetric(start, metricID);
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                        }
                    });
                }
            }
        }
    }

    public void updateMetric(long startTimestamp, MetricID metricID) {
        long duration = System.nanoTime() - startTimestamp;
        MetricRegistry registry = MetricRegistries.get(MetricRegistry.Type.BASE);
        registry.getSimpleTimers().get(metricID).update(Duration.ofNanos(duration));
    }

    @Override
    public void destroy() {
    }

}
