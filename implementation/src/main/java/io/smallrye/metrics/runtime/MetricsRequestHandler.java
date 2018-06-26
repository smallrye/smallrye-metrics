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
package io.smallrye.metrics.runtime;

import io.smallrye.metrics.runtime.exporters.Exporter;
import io.smallrye.metrics.runtime.exporters.JsonExporter;
import io.smallrye.metrics.runtime.exporters.JsonMetadataExporter;
import io.smallrye.metrics.runtime.exporters.PrometheusExporter;
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
     * @return An exporter instance or null in case no matching exporter existed.
     *
     * @see io.smallrye.metrics.tck.rest.MetricsHttpServlet
     */
    public void handleRequest(String requestPath,
                              String method,
                              Stream<String> acceptHeaders,
                              Responder responder) throws IOException {
        Exporter exporter = obtainExporter(method, acceptHeaders);
        if (exporter == null) {
            responder.respondWith(406, "No exporter found for method " + method + " and media type", Collections.emptyMap());
            return;
        }

        String scopePath = requestPath.substring(8);
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
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     *
     * @return An exporter instance or null in case no matching exporter existed.
     */
    private Exporter obtainExporter(String method, Stream<String> acceptHeaders) {
        Exporter exporter;

        if (acceptHeaders == null) {
            if (method.equals("GET")) {
                exporter = new PrometheusExporter();
            } else {
                return null;
            }
        } else {
            // Header can look like "application/json, text/plain, */*"
            if (acceptHeaders.findFirst().map(e -> e.startsWith("application/json")).orElse(false)) {


                if (method.equals("GET")) {
                    exporter = new JsonExporter();
                } else if (method.equals("OPTIONS")) {
                    exporter = new JsonMetadataExporter();
                } else {
                    return null;
                }
            } else {
                // This is the fallback, but only for GET, as Prometheus does not support OPTIONS
                if (method.equals("GET")) {
                    exporter = new PrometheusExporter();
                } else {
                    return null;
                }
            }
        };
        return exporter;
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
