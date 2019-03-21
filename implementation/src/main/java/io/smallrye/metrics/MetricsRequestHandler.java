/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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
package io.smallrye.metrics;

import io.smallrye.metrics.exporters.Exporter;
import io.smallrye.metrics.exporters.JsonExporter;
import io.smallrye.metrics.exporters.JsonMetadataExporter;
import io.smallrye.metrics.exporters.PrometheusExporter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 6/25/18
 */
@ApplicationScoped
public class MetricsRequestHandler {

    private static final Map<String, String> corsHeaders;

    static {
        corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        corsHeaders.put("Access-Control-Allow-Credentials", "true");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }

    /**
     *
     * @param requestPath e.g. request.getRequestURI for an HttpServlet
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder a method that returns a response to the caller. See {@link Responder}
     *
     * @throws IOException rethrows IOException if thrown by the responder
     *
     * You can find example usage in the tests, in io.smallrye.metrics.tck.rest.MetricsHttpServlet
     */
    public void handleRequest(String requestPath,
                              String method,
                              Stream<String> acceptHeaders,
                              Responder responder) throws IOException {
        handleRequest(requestPath, "/metrics", method, acceptHeaders, responder);
    }

    /**
     *
     * @param requestPath e.g. request.getRequestURI for an HttpServlet
     * @param contextRoot the root at which Metrics are exposed, usually "/metrics"
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder a method that returns a response to the caller. See {@link Responder}
     *
     * @throws IOException rethrows IOException if thrown by the responder
     *
     * You can find example usage in the tests, in io.smallrye.metrics.tck.rest.MetricsHttpServlet
     */
    public void handleRequest(String requestPath,
                              String contextRoot,
                              String method,
                              Stream<String> acceptHeaders,
                              Responder responder) throws IOException {
        Exporter exporter = obtainExporter(method, acceptHeaders, responder);
        if (exporter == null) {
            return;
        }

        if(!requestPath.startsWith(contextRoot)) {
            responder.respondWith(500, "The expected context root of metrics is "
                    + contextRoot + ", but a request with a different path was routed to MetricsRequestHandler", Collections.emptyMap());
            return;
        }

        String scopePath = requestPath.substring(contextRoot.length());
        if (scopePath.startsWith("/")) {
            scopePath = scopePath.substring(1);
        }
        if (scopePath.endsWith("/")) {
            scopePath = scopePath.substring(0, scopePath.length() - 1);
        }

        StringBuffer sb;
        if (scopePath.isEmpty()) {
            // All metrics
            sb = exporter.exportAllScopes();

        } else if (scopePath.contains("/")) {
            // One metric in a scope

            String attribute = scopePath.substring(scopePath.indexOf('/') + 1);

            MetricRegistry.Type scope = getScopeFromPath(responder, scopePath.substring(0, scopePath.indexOf('/')));
            if (scope == null) {
                responder.respondWith(404, "Scope " + scopePath + " not found", Collections.emptyMap());
                return;
            }

            MetricRegistry registry = MetricRegistries.get(scope);
            Map<String, Metric> metricValuesMap = registry.getMetrics();

            if (metricValuesMap.containsKey(attribute)) {
                sb = exporter.exportOneMetric(scope, attribute);
            } else {
                responder.respondWith( 404, "Metric " + scopePath + " not found", Collections.emptyMap());
                return;
            }
        } else {
            // A single scope

            MetricRegistry.Type scope = getScopeFromPath(responder, scopePath);
            if (scope == null) {
                responder.respondWith( 404, "Scope " + scopePath + " not found", Collections.emptyMap());
                return;
            }

            MetricRegistry reg = MetricRegistries.get(scope);
            if (reg.getMetadata().size() == 0) {
                responder.respondWith( 204, "No data in scope " + scopePath, Collections.emptyMap());
                return;
            }

            sb = exporter.exportOneScope(scope);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", exporter.getContentType());
        headers.put("Access-Control-Max-Age", "1209600");
        headers.putAll(corsHeaders);

        responder.respondWith(200, sb.toString(), headers);

    }

    private MetricRegistry.Type getScopeFromPath(Responder responder, String scopePath) throws IOException {
        MetricRegistry.Type scope;
        try {
            scope = MetricRegistry.Type.valueOf(scopePath.toUpperCase());
        } catch (IllegalArgumentException iae) {
            responder.respondWith(404, "Bad scope requested: " + scopePath, Collections.emptyMap());
            return null;
        }
        return scope;
    }


    /**
     * Determine which exporter we want.
     *
     * @param method        http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder     the responder to use if an error occurs
     * @return An exporter instance. If an exporter cannot be obtained for some reason, this method will use the responder
     * to inform the user and will return null.
     */
    private Exporter obtainExporter(String method, Stream<String> acceptHeaders, Responder responder) throws IOException {
        if (!method.equals("GET") && !method.equals("OPTIONS")) {
            responder.respondWith(405, "Only GET and OPTIONS methods are accepted.", Collections.emptyMap());
            return null;
        } else if (acceptHeaders != null && acceptHeaders.findFirst().map(e -> e.startsWith("application/json")).orElse(false)) {
            // use JSON exporter
            if (method.equals("GET")) {
                return new JsonExporter();
            } else {
                return new JsonMetadataExporter();
            }
        } else {
            // use Prometheus exporter
            if (method.equals("GET")) {
                return new PrometheusExporter();
            } else {
                responder.respondWith(406, "OPTIONS method is only allowed with application/json media type.", Collections.emptyMap());
                return null;
            }
        }
    }


    /**
     * Responder is used by MetricsRequestHandler to return a response to the caller
     */
    public interface Responder {
        /**
         *
         * @param status http status code
         * @param message message to be returned
         * @param headers a map of http headers
         * @throws IOException this method may be implemented to throw an IOException.
         * In such case the {@link MetricsRequestHandler#handleRequest(String, String, Stream, Responder)} will propagate the exception
         */
        void respondWith(int status, String message, Map<String, String> headers) throws IOException;
    }
}
