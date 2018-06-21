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

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author hrupp
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 */
@WebServlet(name = "metrics-servlet", urlPatterns = "/metrics/*")
public class MetricsHttpServlet extends HttpServlet {

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestPath = request.getRequestURI();

        Exporter exporter = obtainExporter(request);
        if (exporter == null) {
            respondWith(response, 406, "No exporter found for method " + request.getMethod() + " and media type");
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

            MetricRegistry.Type scope = getScopeFromPath(response, scopePath.substring(0, scopePath.indexOf('/')));
            if (scope == null) {
                respondWith(response, 404, "Scope " + scopePath + " not found");
                return;
            }

            MetricRegistry registry = registries.get(scope);
            Map<String, Metric> metricValuesMap = registry.getMetrics();

            if (metricValuesMap.containsKey(attribute)) {
                sb = exporter.exportOneMetric(scope, attribute);
            } else {
                respondWith(response, 404, "Metric " + scopePath + " not found");
                return;
            }
        } else {
            // A single scope

            MetricRegistry.Type scope = getScopeFromPath(response, scopePath);
            if (scope == null) {
                respondWith(response, 404, "Scope " + scopePath + " not found");
                return;
            }

            MetricRegistry reg = registries.get(scope);
            if (reg.getMetadata().size() == 0) {
                respondWith(response, 204, "No data in scope " + scopePath);
            }

            sb = exporter.exportOneScope(scope);
        }

        response.addHeader("Content-Type", exporter.getContentType());
        provideCorsHeaders(response);
        response.addHeader("Access-Control-Max-Age", "1209600");
        response.getWriter().write(sb.toString());

    }

    private void respondWith(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(message);
    }

    private void provideCorsHeaders(HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }

    private MetricRegistry.Type getScopeFromPath(HttpServletResponse response, String scopePath) throws IOException {
        MetricRegistry.Type scope;
        try {
            scope = MetricRegistry.Type.valueOf(scopePath.toUpperCase());
        } catch (IllegalArgumentException iae) {
            respondWith(response, 404, "Bad scope requested: " + scopePath);
            return null;
        }
        return scope;
    }


    /**
     * Determine which exporter we want.
     *
     * @param exchange The http exchange coming in
     * @return An exporter instance or null in case no matching exporter existed.
     */
    private Exporter obtainExporter(HttpServletRequest request) {
        Enumeration<String> acceptHeaders = request.getHeaders("Accept");
        Exporter exporter;

        String method = request.getMethod();

        if (acceptHeaders == null) {
            if (method.equals("GET")) {
                exporter = new PrometheusExporter(registries);
            } else {
                return null;
            }
        } else {
            // Header can look like "application/json, text/plain, */*"
            if (acceptHeaders.hasMoreElements() && acceptHeaders.nextElement().startsWith("application/json")) {


                if (method.equals("GET")) {
                    exporter = new JsonExporter(registries);
                } else if (method.equals("OPTIONS")) {
                    exporter = new JsonMetadataExporter(registries);
                } else {
                    return null;
                }
            } else {
                // This is the fallback, but only for GET, as Prometheus does not support OPTIONS
                if (method.equals("GET")) {
                    exporter = new PrometheusExporter(registries);
                } else {
                    return null;
                }
            }
        };
        return exporter;
    }

    @Inject
    private MetricRegistries registries;
}
