/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package io.smallrye.metrics.exporters;

import io.smallrye.metrics.MetricRegistries;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author hrupp
 */
public class JsonExporter implements Exporter {

    private static final Logger log = Logger.getLogger("io.smallrye.metrics");

    private static final String COMMA_LF = ",\n";
    private static final String LF = "\n";

    @Override
    public StringBuffer exportOneScope(MetricRegistry.Type scope) {

        StringBuffer sb = new StringBuffer();

        getMetricsForAScope(sb, scope);

        return sb;
    }

    private void getMetricsForAScope(StringBuffer sb, MetricRegistry.Type scope) {

        MetricRegistry registry = MetricRegistries.get(scope);
        Map<String, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();

        sb.append("{\n");

        writeMetricsForMap(sb, metricMap, metadataMap);

        sb.append(LF).append("}");
    }

    private void writeMetricsForMap(StringBuffer sb, Map<String, Metric> metricMap, Map<String, Metadata> metadataMap) {

        boolean first = true;

        for (Iterator<Map.Entry<String, Metric>> iterator = metricMap.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Metric> entry = iterator.next();
            String key = entry.getKey();

            Metric value = entry.getValue();
            Metadata metadata = metadataMap.get(key);

            if (metadata == null) {
                throw new IllegalArgumentException("MD is null for " + key);
            }

            StringBuffer metricBuffer = new StringBuffer();


            if (first) {
                first = false;
            } else {
                metricBuffer.append(',').append(LF);
            }

            try {
                switch (metadata.getTypeRaw()) {
                    case GAUGE:
                    case COUNTER:
                        Number val = getValueFromMetric(value, key);
                        metricBuffer.append("  ").append('"').append(key).append('"').append(" : ").append(val);
                        break;
                    case METERED:
                        Metered meter = (Metered) value;
                        writeStartLine(metricBuffer, key);
                        writeMeterValues(metricBuffer, meter);
                        writeEndLine(metricBuffer);
                        break;
                    case TIMER:
                        Timer timer = (Timer) value;
                        writeStartLine(metricBuffer, key);
                        writeTimerValues(metricBuffer, timer, metadata.getUnit());
                        writeEndLine(metricBuffer);
                        break;
                    case HISTOGRAM:
                        Histogram hist = (Histogram) value;
                        writeStartLine(metricBuffer, key);
                        metricBuffer.append("    \"count\": ").append(hist.getCount()).append(COMMA_LF);
                        writeSnapshotValues(metricBuffer, hist.getSnapshot());
                        writeEndLine(metricBuffer);
                        break;
                    default:
                        throw new IllegalArgumentException("Not supported: " + key);
                }

                sb.append(metricBuffer);
            } catch (Exception e) {
                log.warn("Unable to export metric " + key, e);
            }
        }
    }

    private void writeEndLine(StringBuffer sb) {
        sb.append("  }");
    }

    private void writeStartLine(StringBuffer sb, String key) {
        sb.append("  ").append('"').append(key).append('"').append(" : ").append("{\n");
    }

    private void writeMeterValues(StringBuffer sb, Metered meter) {
        sb.append("    \"count\": ").append(meter.getCount()).append(COMMA_LF);
        sb.append("    \"meanRate\": ").append(meter.getMeanRate()).append(COMMA_LF);
        sb.append("    \"oneMinRate\": ").append(meter.getOneMinuteRate()).append(COMMA_LF);
        sb.append("    \"fiveMinRate\": ").append(meter.getFiveMinuteRate()).append(COMMA_LF);
        sb.append("    \"fifteenMinRate\": ").append(meter.getFifteenMinuteRate()).append(LF);
    }

    private void writeTimerValues(StringBuffer sb, Timer timer, String unit) {
        writeSnapshotValues(sb, timer.getSnapshot(), unit);
        // Backup and write COMMA_LF
        sb.setLength(sb.length() - 1);
        sb.append(COMMA_LF);
        writeMeterValues(sb, timer);
    }

    private void writeSnapshotValues(StringBuffer sb, Snapshot snapshot) {
        sb.append("    \"p50\": ").append(snapshot.getMedian()).append(COMMA_LF);
        sb.append("    \"p75\": ").append(snapshot.get75thPercentile()).append(COMMA_LF);
        sb.append("    \"p95\": ").append(snapshot.get95thPercentile()).append(COMMA_LF);
        sb.append("    \"p98\": ").append(snapshot.get98thPercentile()).append(COMMA_LF);
        sb.append("    \"p99\": ").append(snapshot.get99thPercentile()).append(COMMA_LF);
        sb.append("    \"p999\": ").append(snapshot.get999thPercentile()).append(COMMA_LF);
        sb.append("    \"min\": ").append(snapshot.getMin()).append(COMMA_LF);
        sb.append("    \"mean\": ").append(snapshot.getMean()).append(COMMA_LF);
        sb.append("    \"max\": ").append(snapshot.getMax()).append(COMMA_LF);
        // Can't be COMMA_LF has there may not be anything following as is the case for a Histogram
        sb.append("    \"stddev\": ").append(snapshot.getStdDev()).append(LF);

    }

    private void writeSnapshotValues(StringBuffer sb, Snapshot snapshot, String unit) {
        sb.append("    \"p50\": ").append(toBase(snapshot.getMedian(), unit)).append(COMMA_LF);
        sb.append("    \"p75\": ").append(toBase(snapshot.get75thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p95\": ").append(toBase(snapshot.get95thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p98\": ").append(toBase(snapshot.get98thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p99\": ").append(toBase(snapshot.get99thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p999\": ").append(toBase(snapshot.get999thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"min\": ").append(toBase(snapshot.getMin(), unit)).append(COMMA_LF);
        sb.append("    \"mean\": ").append(toBase(snapshot.getMean(), unit)).append(COMMA_LF);
        sb.append("    \"max\": ").append(toBase(snapshot.getMax(), unit)).append(COMMA_LF);
        // Can't be COMMA_LF has there may not be anything following as is the case for a Histogram
        sb.append("    \"stddev\": ").append(toBase(snapshot.getStdDev(), unit)).append(LF);

    }

    private Number toBase(Number count, String unit) {
        return ExporterUtil.convertNanosTo(count.doubleValue(), unit);
    }

    private Number getValueFromMetric(Metric theMetric, String name) {
        if (theMetric instanceof Gauge) {
            Number value = (Number) ((Gauge) theMetric).getValue();
            if (value != null) {
                return value;
            } else {
                log.warn("Value is null for " + name);
                return -142.142; // TODO
            }
        } else if (theMetric instanceof Counter) {
            return ((Counter) theMetric).getCount();
        } else {
            log.error("Not yet supported metric: " + theMetric.getClass().getName());
            return -42.42;
        }
    }

    @Override
    public StringBuffer exportAllScopes() {
        StringBuffer sb = new StringBuffer();
        sb.append("{");

        boolean first = true;
        for (MetricRegistry.Type scope : MetricRegistry.Type.values()) {
            MetricRegistry registry = MetricRegistries.get(scope);
            if (registry.getNames().size() > 0) {
                if (!first) {
                    sb.append(",");
                }
                sb.append('"').append(scope.getName().toLowerCase()).append('"').append(" :\n");
                getMetricsForAScope(sb, scope);
                sb.append(JsonExporter.LF);
                first = false;
            }
        }

        sb.append("}");
        return sb;
    }

    @Override
    public StringBuffer exportOneMetric(MetricRegistry.Type scope, String metricName) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<String, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();


        Metric m = metricMap.get(metricName);

        Map<String, Metric> outMap = new HashMap<>(1);
        outMap.put(metricName, m);

        StringBuffer sb = new StringBuffer();
        sb.append("{");
        writeMetricsForMap(sb, outMap, metadataMap);
        sb.append("}");
        sb.append(JsonExporter.LF);

        return sb;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
