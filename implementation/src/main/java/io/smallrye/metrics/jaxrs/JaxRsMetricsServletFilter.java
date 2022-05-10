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
// FIXME: make this use the Micrometer API directly instead of going through the compatibility layer
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
                createMetrics(metricID);
                if (!servletRequest.isAsyncStarted()) {
                    boolean success = servletRequest.getAttribute("smallrye.metrics.jaxrs.successful") != null;
                    update(success, start, metricID);
                } else { // if response is async, update the metric after it really finishes
                    servletRequest.getAsyncContext().addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            boolean success = event.getSuppliedRequest()
                                    .getAttribute("smallrye.metrics.jaxrs.successful") != null;
                            update(success, start, metricID);
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                            boolean success = event.getSuppliedRequest()
                                    .getAttribute("smallrye.metrics.jaxrs.successful") != null;
                            update(success, start, metricID);
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                        }
                    });
                }
            }
        }
    }

    private void update(boolean success, long startTimestamp, MetricID metricID) {
        if (success) {
            updateAfterSuccess(startTimestamp, metricID);
        } else {
            updateAfterFailure(metricID);
        }
    }

    private void updateAfterSuccess(long startTimestamp, MetricID metricID) {
        long duration = System.nanoTime() - startTimestamp;
        MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.BASE);
        registry.getSimpleTimer(metricID).update(Duration.ofNanos(duration));
    }

    private void updateAfterFailure(MetricID metricID) {
        MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.BASE);
        registry.getCounter(transformToMetricIDForFailedRequest(metricID)).inc();
    }

    private MetricID transformToMetricIDForFailedRequest(MetricID metricID) {
        return new MetricID("REST.request.unmappedException.total", metricID.getTagsAsArray());
    }

    private void createMetrics(MetricID metricID) {
        MetricRegistry registry = MetricRegistries.getOrCreate(MetricRegistry.Type.BASE);
        if (registry.getSimpleTimer(metricID) == null) {
            Metadata successMetadata = Metadata.builder()
                    .withName(metricID.getName())
                    .withDescription(
                            "The number of invocations and total response time of this RESTful " +
                                    "resource method since the start of the server.")
                    .withUnit(MetricUnits.NANOSECONDS)
                    .build();
            registry.simpleTimer(successMetadata, metricID.getTagsAsArray());
        }
        MetricID metricIDForFailure = transformToMetricIDForFailedRequest(metricID);
        if (registry.getCounter(metricIDForFailure) == null) {
            Metadata failureMetadata = Metadata.builder()
                    .withName(metricIDForFailure.getName())
                    .withDisplayName("Total Unmapped Exceptions count")
                    .withDescription(
                            "The total number of unmapped exceptions that occurred from this RESTful resource " +
                                    "method since the start of the server.")
                    .build();
            registry.counter(failureMetadata, metricIDForFailure.getTagsAsArray());
        }
    }

    @Override
    public void destroy() {
    }

}
