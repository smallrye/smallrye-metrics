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

import java.io.StringWriter;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

import io.smallrye.metrics.MetricRegistries;

/**
 * @author hrupp
 */
public class JsonExporter implements Exporter {

    @Override
    public StringBuilder exportOneScope(MetricRegistry.Type scope) {
        return stringify(exportOneRegistry(MetricRegistries.get(scope)));
    }

    @Override
    public StringBuilder exportAllScopes() {

        JsonObjectBuilder root = JsonProviderHolder.get().createObjectBuilder();

        root.add("base", exportOneRegistry(MetricRegistries.get(MetricRegistry.Type.BASE)));
        root.add("vendor", exportOneRegistry(MetricRegistries.get(MetricRegistry.Type.VENDOR)));
        root.add("application", exportOneRegistry(MetricRegistries.get(MetricRegistry.Type.APPLICATION)));

        return stringify(root.build());
    }

    @Override
    public StringBuilder exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();

        Metric m = metricMap.get(metricID);

        Map<MetricID, Metric> outMap = new HashMap<>(1);
        outMap.put(metricID, m);

        JsonObjectBuilder root = JsonProviderHolder.get().createObjectBuilder();
        exportMetricsForMap(outMap, metadataMap)
                .forEach(root::add);
        return stringify(root.build());
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

        JsonObjectBuilder root = JsonProviderHolder.get().createObjectBuilder();
        exportMetricsForMap(metricMap, metadataMap)
                .forEach(root::add);
        return stringify(root.build());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    private static final Map<String, ?> JSON_CONFIG = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true);

    StringBuilder stringify(JsonObject obj) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = JsonProviderHolder.get().createWriterFactory(JSON_CONFIG).createWriter(out)) {
            writer.writeObject(obj);
        }
        return new StringBuilder(out.toString());
    }

    private Map<String, JsonValue> exportMetricsByName(Map<MetricID, Metric> metricMap, Metadata metadata) {
        Map<String, JsonValue> result = new HashMap<>();
        JsonObjectBuilder builder = JsonProviderHolder.get().createObjectBuilder();
        switch (metadata.getTypeRaw()) {
            case GAUGE:
            case COUNTER:
                metricMap.forEach((metricID, metric) -> {
                    result.put(metricID.getName() + createTagsString(metricID.getTagsAsList()),
                            exportSimpleMetric(metricID, metric));
                });
                break;
            case METERED:
                metricMap.forEach((metricID, value) -> {
                    Metered metric = (Metered) value;
                    meterValues(metric, createTagsString(metricID.getTagsAsList()))
                            .forEach(builder::add);
                });
                result.put(metadata.getName(), builder.build());
                break;
            case CONCURRENT_GAUGE:
                metricMap.forEach((metricID, value) -> {
                    ConcurrentGauge metric = (ConcurrentGauge) value;
                    exportConcurrentGauge(metric, createTagsString(metricID.getTagsAsList()))
                            .forEach(builder::add);
                });
                result.put(metadata.getName(), builder.build());
                break;
            case SIMPLE_TIMER:
                metricMap.forEach((metricID, value) -> {
                    SimpleTimer metric = (SimpleTimer) value;
                    exportSimpleTimer(metric, metadata.getUnit(), createTagsString(metricID.getTagsAsList()))
                            .forEach(builder::add);
                });
                result.put(metadata.getName(), builder.build());
                break;
            case TIMER:
                metricMap.forEach((metricID, value) -> {
                    Timer metric = (Timer) value;
                    exportTimer(metric, metadata.getUnit(), createTagsString(metricID.getTagsAsList()))
                            .forEach(builder::add);
                });
                result.put(metadata.getName(), builder.build());
                break;
            case HISTOGRAM:
                metricMap.forEach((metricID, value) -> {
                    Histogram metric = (Histogram) value;
                    exportHistogram(metric, createTagsString(metricID.getTagsAsList()))
                            .forEach(builder::add);
                });
                result.put(metadata.getName(), builder.build());
                break;
            default:
                throw new IllegalArgumentException("Not supported: " + metadata.getTypeRaw());
        }
        return result;
    }

    private JsonObject exportOneRegistry(MetricRegistry registry) {
        Map<MetricID, Metric> metricMap = registry.getMetrics();
        Map<String, Metadata> metadataMap = registry.getMetadata();

        JsonObjectBuilder root = JsonProviderHolder.get().createObjectBuilder();
        exportMetricsForMap(metricMap, metadataMap)
                .forEach(root::add);
        return root.build();
    }

    private Map<String, JsonValue> exportMetricsForMap(Map<MetricID, Metric> metricMap, Map<String, Metadata> metadataMap) {
        Map<String, JsonValue> result = new HashMap<>();

        // split into groups by metric name
        Map<String, Map<MetricID, Metric>> metricsGroupedByName = metricMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getKey().getName(),
                        Collectors.mapping(e -> e, Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        // and then for each group, perform the export
        metricsGroupedByName.entrySet().stream()
                .map(entry -> exportMetricsByName(entry.getValue(), metadataMap.get(entry.getKey())))
                .forEach(map -> {
                    map.forEach(result::put);
                });
        return result;
    }

    private JsonValue exportSimpleMetric(MetricID metricID, Metric metric) {
        Number val = getValueFromMetric(metric, metricID.getName());
        if (val instanceof Double) {
            return JsonProviderHolder.get().createValue((Double) val);
        } else if (val instanceof Float) {
            return JsonProviderHolder.get().createValue((Float) val);
        } else if (val instanceof Integer) {
            return JsonProviderHolder.get().createValue((Integer) val);
        } else if (val instanceof Long) {
            return JsonProviderHolder.get().createValue((Long) val);
        } else {
            throw new IllegalStateException();
        }
    }

    private Map<String, JsonValue> meterValues(Metered meter, String tags) {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("count" + tags, JsonProviderHolder.get().createValue(meter.getCount()));
        map.put("meanRate" + tags, JsonProviderHolder.get().createValue(meter.getMeanRate()));
        map.put("oneMinRate" + tags, JsonProviderHolder.get().createValue(meter.getOneMinuteRate()));
        map.put("fiveMinRate" + tags, JsonProviderHolder.get().createValue(meter.getFiveMinuteRate()));
        map.put("fifteenMinRate" + tags, JsonProviderHolder.get().createValue(meter.getFifteenMinuteRate()));
        return map;
    }

    private Map<String, JsonValue> exportConcurrentGauge(ConcurrentGauge concurrentGauge, String tags) {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("current" + tags, JsonProviderHolder.get().createValue(concurrentGauge.getCount()));
        map.put("max" + tags, JsonProviderHolder.get().createValue(concurrentGauge.getMax()));
        map.put("min" + tags, JsonProviderHolder.get().createValue(concurrentGauge.getMin()));
        return map;
    }

    private JsonObject exportSimpleTimer(SimpleTimer timer, String unit, String tags) {
        JsonObjectBuilder builder = JsonProviderHolder.get().createObjectBuilder();
        builder.add("count" + tags, timer.getCount());
        builder.add("elapsedTime" + tags, toBase(timer.getElapsedTime().toNanos(), unit));
        Duration minTimeDuration = timer.getMinTimeDuration();
        if (minTimeDuration != null) {
            builder.add("minTimeDuration" + tags, toBase(minTimeDuration.toNanos(), unit));
        } else {
            builder.add("minTimeDuration" + tags, JsonValue.NULL);
        }
        Duration maxTimeDuration = timer.getMaxTimeDuration();
        if (maxTimeDuration != null) {
            builder.add("maxTimeDuration" + tags, toBase(maxTimeDuration.toNanos(), unit));
        } else {
            builder.add("maxTimeDuration" + tags, JsonValue.NULL);
        }
        return builder.build();
    }

    private JsonObject exportTimer(Timer timer, String unit, String tags) {
        JsonObjectBuilder builder = JsonProviderHolder.get().createObjectBuilder();
        snapshotValues(timer.getSnapshot(), unit, tags)
                .forEach(builder::add);
        meterValues(timer, tags)
                .forEach(builder::add);
        builder.add("elapsedTime" + tags, toBase(timer.getElapsedTime().toNanos(), unit));
        return builder.build();
    }

    private Map<String, JsonValue> exportHistogram(Histogram histogram, String tags) {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("count" + tags, JsonProviderHolder.get().createValue(histogram.getCount()));
        snapshotValues(histogram.getSnapshot(), tags)
                .forEach((map::put));
        return map;
    }

    private Map<String, JsonValue> snapshotValues(Snapshot snapshot, String tags) {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("p50" + tags, JsonProviderHolder.get().createValue(snapshot.getMedian()));
        map.put("p75" + tags, JsonProviderHolder.get().createValue(snapshot.get75thPercentile()));
        map.put("p95" + tags, JsonProviderHolder.get().createValue(snapshot.get95thPercentile()));
        map.put("p98" + tags, JsonProviderHolder.get().createValue(snapshot.get98thPercentile()));
        map.put("p99" + tags, JsonProviderHolder.get().createValue(snapshot.get99thPercentile()));
        map.put("p999" + tags, JsonProviderHolder.get().createValue(snapshot.get999thPercentile()));
        map.put("min" + tags, JsonProviderHolder.get().createValue(snapshot.getMin()));
        map.put("mean" + tags, JsonProviderHolder.get().createValue(snapshot.getMean()));
        map.put("max" + tags, JsonProviderHolder.get().createValue(snapshot.getMax()));
        map.put("stddev" + tags, JsonProviderHolder.get().createValue(snapshot.getStdDev()));
        return map;
    }

    private Map<String, JsonValue> snapshotValues(Snapshot snapshot, String unit, String tags) {
        Map<String, JsonValue> map = new HashMap<>();
        map.put("p50" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.getMedian(), unit)));
        map.put("p75" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.get75thPercentile(), unit)));
        map.put("p95" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.get95thPercentile(), unit)));
        map.put("p98" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.get98thPercentile(), unit)));
        map.put("p99" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.get99thPercentile(), unit)));
        map.put("p999" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.get999thPercentile(), unit)));
        map.put("min" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.getMin(), unit)));
        map.put("mean" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.getMean(), unit)));
        map.put("max" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.getMax(), unit)));
        map.put("stddev" + tags, JsonProviderHolder.get().createValue(toBase(snapshot.getStdDev(), unit)));
        return map;
    }

    private Double toBase(Number count, String unit) {
        return ExporterUtil.convertNanosTo(count.doubleValue(), unit);
    }

    private Number getValueFromMetric(Metric theMetric, String name) {
        if (theMetric instanceof Gauge) {
            Number value = (Number) ((Gauge) theMetric).getValue();
            if (value != null) {
                return value;
            } else {
                return 0;
            }
        } else if (theMetric instanceof Counter) {
            return ((Counter) theMetric).getCount();
        } else {
            return null;
        }
    }

    /**
     * Converts a list of tags to the string that will be appended to the metric name in JSON output.
     * If there are no tags, this returns an empty string.
     */
    private String createTagsString(List<Tag> tagsAsList) {
        if (tagsAsList == null || tagsAsList.isEmpty()) {
            return "";
        } else {
            return ";" + tagsAsList.stream()
                    .map(tag -> tag.getTagName() + "=" + tag.getTagValue()
                            .replaceAll(";", "_"))
                    //                            .replaceAll("\"", "\\\\\""))  // this is done by JSON-P automatically
                    //                            .replaceAll("\n", "\\\\n"))  // this is done by JSON-P automatically
                    .collect(Collectors.joining(";"));
        }
    }

}
