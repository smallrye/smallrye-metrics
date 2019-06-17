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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.jboss.logging.Logger;

import io.smallrye.metrics.MetricRegistries;

/**
 * @author hrupp
 */
public class JsonExporter implements Exporter {

    private static final Logger log = Logger.getLogger("io.smallrye.metrics");

    private static final String COMMA_LF = ",\n";
    private static final String LF = "\n";

    @Override
    public StringBuilder exportOneScope(MetricRegistry.Type scope) {

        StringBuilder sb = new StringBuilder();

        getMetricsForAScope(sb, scope);

        return sb;
    }

    @Override
    public StringBuilder exportAllScopes() {
        StringBuilder sb = new StringBuilder();
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
    public StringBuilder exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();

        Metric m = metricMap.get(metricID);

        Map<MetricID, Metric> outMap = new HashMap<>(1);
        outMap.put(metricID, m);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        writeMetricsForMap(sb, outMap, metadataMap);
        sb.append("}");
        sb.append(JsonExporter.LF);

        return sb;
    }

    @Override
    public StringBuilder exportMetricsByName(MetricRegistry.Type scope, String name) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().getName().equals(name))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
        Map<String, Metadata> metadataMap = registry.getMetadata();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(JsonExporter.LF);
        writeMetricsForMap(sb, metricMap, metadataMap);
        sb.append(JsonExporter.LF);
        sb.append("}");
        sb.append(JsonExporter.LF);

        return sb;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private StringBuilder writeMetricsByName(Map<MetricID, Metric> metricMap, Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        switch (metadata.getTypeRaw()) {
            case GAUGE:
            case COUNTER:
                String metricsString = metricMap.entrySet()
                        .stream()
                        .map(metric -> writeOneSimpleMetric(metric.getKey(), metric.getValue(), metadata))
                        .collect(Collectors.joining(COMMA_LF));
                sb.append(metricsString);
                break;
            case METERED:
                sb.append(writeMeters(metricMap, metadata));
                break;
            case CONCURRENT_GAUGE:
                sb.append(writeConcurrentGauges(metricMap, metadata));
                break;
            case TIMER:
                sb.append(writeTimers(metricMap, metadata));
                break;
            case HISTOGRAM:
                sb.append(writeHistograms(metricMap, metadata));
                break;
            default:
                throw new IllegalArgumentException("Not supported: " + metadata.getTypeRaw());
        }
        return sb;
    }

    private void getMetricsForAScope(StringBuilder sb, MetricRegistry.Type scope) {

        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();

        sb.append("{\n");

        writeMetricsForMap(sb, metricMap, metadataMap);

        sb.append(LF).append("}");
    }

    private void writeMetricsForMap(StringBuilder outSb, Map<MetricID, Metric> metricMap, Map<String, Metadata> metadataMap) {
        // split into groups by metric name
        Map<String, Map<MetricID, Metric>> metricsGroupedByName = metricMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getName(),
                        Collectors.mapping(e -> e, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        // and then for each group, perform the export
        String result = metricsGroupedByName.entrySet().stream()
                .map(entry -> writeMetricsByName(entry.getValue(), metadataMap.get(entry.getKey())))
                .collect(Collectors.joining(COMMA_LF));
        outSb.append(result);
    }

    private void writeEndLine(StringBuilder sb) {
        sb.append("  }");
    }

    private void writeStartLine(StringBuilder sb, String key) {
        sb.append("  ").append('"').append(key).append('"').append(" : ").append("{\n");
    }

    private StringBuilder writeOneSimpleMetric(MetricID metricID, Metric metric, Metadata metadata) {
        StringBuilder result = new StringBuilder();
        Number val = getValueFromMetric(metric, metricID.getName());
        String tags = createTagsString(metricID.getTagsAsList());
        result.append("  ").append('"').append(metricID.getName()).append(tags).append('"').append(" : ").append(val);
        return result;
    }

    private StringBuilder writeMeters(Map<MetricID, Metric> metricMap, Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metricMap.size() > 0) {
            writeStartLine(sb, metadata.getName());
            String values = metricMap.entrySet()
                    .stream()
                    .map(e -> writeMeterValues((Metered) e.getValue(), createTagsString(e.getKey().getTagsAsList())))
                    .collect(Collectors.joining(COMMA_LF));
            sb.append(values).append(LF);
            writeEndLine(sb);
        }
        return sb;
    }

    private StringBuilder writeHistograms(Map<MetricID, Metric> metricMap, Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metricMap.size() > 0) {
            writeStartLine(sb, metadata.getName());
            String values = metricMap.entrySet()
                    .stream()
                    .map(e -> {
                        String tags = createTagsString(e.getKey().getTagsAsList());
                        long count = ((Histogram) e.getValue()).getCount();
                        return new StringBuilder().append("    \"count").append(tags).append("\": ").append(count)
                                .append(COMMA_LF)
                                .append(writeSnapshotValues(((Histogram) e.getValue()).getSnapshot(), tags));
                    })
                    .collect(Collectors.joining(COMMA_LF));
            sb.append(values).append(LF);
            writeEndLine(sb);
        }
        return sb;
    }

    private StringBuilder writeConcurrentGauges(Map<MetricID, Metric> metricMap, Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metricMap.size() > 0) {
            writeStartLine(sb, metadata.getName());
            String values = metricMap.entrySet()
                    .stream()
                    .map(e -> writeConcurrentGaugeValues((ConcurrentGauge) e.getValue(),
                            createTagsString(e.getKey().getTagsAsList())))
                    .collect(Collectors.joining(COMMA_LF));
            sb.append(values).append(LF);
            writeEndLine(sb);
        }
        return sb;
    }

    private StringBuilder writeTimers(Map<MetricID, Metric> metricMap, Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        if (metricMap.size() > 0) {
            writeStartLine(sb, metadata.getName());
            String values = metricMap.entrySet()
                    .stream()
                    .map(e -> writeTimerValues((Timer) e.getValue(),
                            metadata.getUnit().orElse(null),
                            createTagsString(e.getKey().getTagsAsList())))
                    .collect(Collectors.joining(COMMA_LF));
            sb.append(values).append(LF);
            writeEndLine(sb);
        }
        return sb;
    }

    private StringBuilder writeMeterValues(Metered meter, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"count").append(tags).append("\": ").append(meter.getCount()).append(COMMA_LF);
        sb.append("    \"meanRate").append(tags).append("\": ").append(meter.getMeanRate()).append(COMMA_LF);
        sb.append("    \"oneMinRate").append(tags).append("\": ").append(meter.getOneMinuteRate()).append(COMMA_LF);
        sb.append("    \"fiveMinRate").append(tags).append("\": ").append(meter.getFiveMinuteRate()).append(COMMA_LF);
        sb.append("    \"fifteenMinRate").append(tags).append("\": ").append(meter.getFifteenMinuteRate());
        return sb;
    }

    private StringBuilder writeConcurrentGaugeValues(ConcurrentGauge concurrentGauge, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"current").append(tags).append("\": ").append(concurrentGauge.getCount()).append(COMMA_LF);
        sb.append("    \"max").append(tags).append("\": ").append(concurrentGauge.getMax()).append(COMMA_LF);
        sb.append("    \"min").append(tags).append("\": ").append(concurrentGauge.getMin());
        return sb;
    }

    private StringBuilder writeTimerValues(Timer timer, String unit, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append(writeSnapshotValues(timer.getSnapshot(), unit, tags));
        sb.append(COMMA_LF);
        sb.append(writeMeterValues(timer, tags));
        return sb;
    }

    private StringBuilder writeSnapshotValues(Snapshot snapshot, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"p50").append(tags).append("\": ").append(snapshot.getMedian()).append(COMMA_LF);
        sb.append("    \"p75").append(tags).append("\": ").append(snapshot.get75thPercentile()).append(COMMA_LF);
        sb.append("    \"p95").append(tags).append("\": ").append(snapshot.get95thPercentile()).append(COMMA_LF);
        sb.append("    \"p98").append(tags).append("\": ").append(snapshot.get98thPercentile()).append(COMMA_LF);
        sb.append("    \"p99").append(tags).append("\": ").append(snapshot.get99thPercentile()).append(COMMA_LF);
        sb.append("    \"p999").append(tags).append("\": ").append(snapshot.get999thPercentile()).append(COMMA_LF);
        sb.append("    \"min").append(tags).append("\": ").append(snapshot.getMin()).append(COMMA_LF);
        sb.append("    \"mean").append(tags).append("\": ").append(snapshot.getMean()).append(COMMA_LF);
        sb.append("    \"max").append(tags).append("\": ").append(snapshot.getMax()).append(COMMA_LF);
        sb.append("    \"stddev").append(tags).append("\": ").append(snapshot.getStdDev());
        return sb;
    }

    private StringBuilder writeSnapshotValues(Snapshot snapshot, String unit, String tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"p50").append(tags).append("\": ").append(toBase(snapshot.getMedian(), unit)).append(COMMA_LF);
        sb.append("    \"p75").append(tags).append("\": ").append(toBase(snapshot.get75thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p95").append(tags).append("\": ").append(toBase(snapshot.get95thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p98").append(tags).append("\": ").append(toBase(snapshot.get98thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p99").append(tags).append("\": ").append(toBase(snapshot.get99thPercentile(), unit)).append(COMMA_LF);
        sb.append("    \"p999").append(tags).append("\": ").append(toBase(snapshot.get999thPercentile(), unit))
                .append(COMMA_LF);
        sb.append("    \"min").append(tags).append("\": ").append(toBase(snapshot.getMin(), unit)).append(COMMA_LF);
        sb.append("    \"mean").append(tags).append("\": ").append(toBase(snapshot.getMean(), unit)).append(COMMA_LF);
        sb.append("    \"max").append(tags).append("\": ").append(toBase(snapshot.getMax(), unit)).append(COMMA_LF);
        sb.append("    \"stddev").append(tags).append("\": ").append(toBase(snapshot.getStdDev(), unit));
        return sb;
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

    /**
     * Converts a list of tags to the string that will be appended to the metric name in JSON output.
     * If there are no tags, this returns an empty string.
     */
    private String createTagsString(List<Tag> tagsAsList) {
        if (tagsAsList == null || tagsAsList.isEmpty())
            return "";
        else {
            return ";" + tagsAsList.stream()
                    .map(tag -> tag.getTagName() + "=" + tag.getTagValue()
                            .replaceAll(";", "_")
                            .replaceAll("\"", "\\\\\"")
                            .replaceAll("\n", "\\\\n"))
                    .collect(Collectors.joining(";"));
        }
    }

}
