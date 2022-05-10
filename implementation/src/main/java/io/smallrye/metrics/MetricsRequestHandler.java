package io.smallrye.metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.metrics.MetricRegistry;

import io.smallrye.metrics.exporters.Exporter;
import io.smallrye.metrics.exporters.OpenMetricsExporter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@ApplicationScoped
public class MetricsRequestHandler {

    private static final Map<String, String> corsHeaders;
    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";
    private static final String STAR_STAR = "*/*";

    static {
        corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        corsHeaders.put("Access-Control-Allow-Credentials", "true");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }

    /**
     * @param requestPath e.g. request.getRequestURI for an HttpServlet
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder a method that returns a response to the caller. See {@link Responder}
     * @throws IOException rethrows IOException if thrown by the responder
     *
     *         You can find example usage in the tests, in io.smallrye.metrics.tck.rest.MetricsHttpServlet
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
     *         You can find example usage in the tests, in io.smallrye.metrics.tck.rest.MetricsHttpServlet
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

        if (!requestPath.startsWith(contextRoot)) {
            responder.respondWith(500, "The expected context root of metrics is "
                    + contextRoot + ", but a request with a different path was routed to MetricsRequestHandler",
                    Collections.emptyMap());
            return;
        }

        String scopePath = requestPath.substring(contextRoot.length());
        if (scopePath.startsWith("/")) {
            scopePath = scopePath.substring(1);
        }
        if (scopePath.endsWith("/")) {
            scopePath = scopePath.substring(0, scopePath.length() - 1);
        }

        String output;
        if (scopePath.isEmpty()) {
            // All metrics
            output = exporter.exportAllScopes();

        } else if (scopePath.contains("/")) {
            // One metric name in a scope

            String metricName = scopePath.substring(scopePath.indexOf('/') + 1);

            MetricRegistry.Type scope = getScopeFromPath(scopePath.substring(0, scopePath.indexOf('/')));
            if (scope == null) {
                responder.respondWith(404, "Scope " + scopePath + " not found", Collections.emptyMap());
                return;
            }

            MetricRegistry registry = MetricRegistries.getOrCreate(scope);

            // output = exporter.exportMetricsByName(scope, metricName);

            //XXX: Better error handling? exceptions?
            if (registry instanceof LegacyMetricRegistryAdapter &&
                    ((LegacyMetricRegistryAdapter) registry).getPrometheusMeterRegistry().find(metricName).meters()
                            .size() != 0) {
                output = exporter.exportMetricsByName(scope, metricName);
            } else {
                responder.respondWith(404, "Metric " + scopePath + " not found", Collections.emptyMap());
                return;
            }

        } else {
            // A single scope

            MetricRegistry.Type scope = getScopeFromPath(scopePath);
            if (scope == null) {
                responder.respondWith(404, "Scope " + scopePath + " not found", Collections.emptyMap());
                return;
            }

            MetricRegistry reg = MetricRegistries.getOrCreate(scope);

            //XXX:  Re-evaluate: other types of "MeterRegistries".. prolly not, this is an OM exporter
            //Cast to LegacyMetricRegistryAdapter and check that registry contains meters
            if (reg instanceof LegacyMetricRegistryAdapter &&
                    ((LegacyMetricRegistryAdapter) reg).getPrometheusMeterRegistry().getMeters().size() != 0) {
                output = exporter.exportOneScope(scope);
            } else {
                responder.respondWith(204, "No data in scope " + scopePath, Collections.emptyMap());
                return;
            }

        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", exporter.getContentType());
        headers.put("Access-Control-Max-Age", "1209600");
        headers.putAll(corsHeaders);

        responder.respondWith(200, output, headers);
    }

    private MetricRegistry.Type getScopeFromPath(String scopePath) throws IOException {
        MetricRegistry.Type scope;
        try {
            scope = MetricRegistry.Type.valueOf(scopePath.toUpperCase());
        } catch (IllegalArgumentException iae) {
            return null;
        }
        return scope;
    }

    /**
     * Determine which exporter we want.
     *
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder the responder to use if an error occurs
     * @return An exporter instance. If an exporter cannot be obtained for some reason, this method will use the responder
     *         to inform the user and will return null.
     */
    private Exporter obtainExporter(String method, Stream<String> acceptHeaders, Responder responder) throws IOException {
        if (!method.equals("GET") && !method.equals("OPTIONS")) {
            responder.respondWith(405, "Only GET and OPTIONS methods are accepted.", Collections.emptyMap());
            return null;
        } else if (acceptHeaders == null) {
            // use OpenMetrics exporter
            if (method.equals("GET")) {
                return new OpenMetricsExporter();
            } else {
                responder.respondWith(405, "OPTIONS method is only allowed with application/json media type.",
                        Collections.emptyMap());
                return null;
            }
        } else {
            // Header can look like "application/json, text/plain, */*"
            Optional<String> mt = getBestMatchingMediaType(acceptHeaders);
            if (mt.isPresent()) {
                String mediaType = mt.get();

                if (mediaType.startsWith(APPLICATION_JSON)) {

                    throw new UnsupportedOperationException("JSON export not implemented yet");
                    //                    if (method.equals("GET")) {
                    //                        return new JsonExporter();
                    //                    } else {
                    //                        return new JsonMetadataExporter();
                    //                    }
                } else {
                    // This is the fallback, but only for GET, as OpenMetrics does not support OPTIONS
                    if (method.equals("GET")) {
                        return new OpenMetricsExporter();
                    } else {
                        responder.respondWith(406, "OPTIONS method is only allowed with application/json media type.",
                                Collections.emptyMap());
                        return null;
                    }
                }
            } else {
                responder.respondWith(406, "Couldn't determine a suitable media type for the given Accept header.",
                        Collections.emptyMap());
                return null;
            }
        }
    }

    /**
     * Find the best matching media type (i.e. the one with highest prio.
     * If two have the same prio, and one is text/plain, then use this.
     * Return empty if no match can be found
     *
     * @param acceptHeaders A steam of Accept: headers
     * @return best media type as string or null if no match
     */
    // This should be available somewhere in http handling world
    Optional<String> getBestMatchingMediaType(Stream<String> acceptHeaders) {

        List<WTTuple> tupleList = new ArrayList<>();

        // Dissect the heades into type and prio and put them in a list
        acceptHeaders.forEach(h -> {
            String[] headers = h.split(",");
            for (String header : headers) {
                String[] parts = header.split(";");
                float prio = 1.0f;
                if (parts.length > 1) {
                    for (String x : parts) {
                        if (x.startsWith("q=")) {
                            prio = Float.parseFloat(x.substring(2));
                        }
                    }
                }
                WTTuple t = new WTTuple(prio, parts[0]);
                tupleList.add(t);
            }
        });

        WTTuple bestMatchTuple = new WTTuple(-1, null);

        // Iterate over the list and find the best match
        for (WTTuple tuple : tupleList) {
            if (!isKnownMediaType(tuple)) {
                continue;
            }
            if (tuple.weight > bestMatchTuple.weight) {
                bestMatchTuple = tuple;
            } else if (tuple.weight == bestMatchTuple.weight) {
                if (!bestMatchTuple.type.equals(TEXT_PLAIN) && tuple.type.equals(TEXT_PLAIN)) {
                    bestMatchTuple = tuple;
                }
            }
        }

        // We found a match. Now if this is */* return text/plain. Otherwise return the type found
        if (bestMatchTuple.weight > 0) {
            return bestMatchTuple.type.equals(STAR_STAR) ? Optional.of(TEXT_PLAIN) : Optional.of(bestMatchTuple.type);
        }

        // No match
        return Optional.empty();
    }

    private boolean isKnownMediaType(WTTuple tuple) {
        return tuple.type.equals(TEXT_PLAIN) || tuple.type.equals(APPLICATION_JSON) || tuple.type.equals(STAR_STAR);
    }

    /**
     * Responder is used by MetricsRequestHandler to return a response to the caller
     */
    public interface Responder {
        /**
         * @param status http status code
         * @param message message to be returned
         * @param headers a map of http headers
         * @throws IOException this method may be implemented to throw an IOException.
         *         In such case the {@link MetricsRequestHandler#handleRequest(String, String, Stream, Responder)} will
         *         propagate the exception
         */
        void respondWith(int status, String message, Map<String, String> headers) throws IOException;
    }

    /**
     * Helper object for media type matching
     */
    private static class WTTuple {
        float weight;
        String type;

        WTTuple(float weight, String type) {
            this.weight = weight;
            this.type = type;
        }
    }

}
