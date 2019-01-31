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
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Export data in Prometheus text format
 *
 * @author Heiko W. Rupp
 */
// TODO export multiple metrics by one name with different tags
public class PrometheusExporter implements Exporter {

    private static final Logger log = Logger.getLogger("io.smallrye.metrics");

    // This allows to suppress the (noisy) # HELP line
    private static final String MICROPROFILE_METRICS_OMIT_HELP_LINE = "microprofile.metrics.omitHelpLine";
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("(?<=[a-z])[A-Z]");


    private static final String LF = "\n";
    private static final String GAUGE = "gauge";
    private static final String SPACE = " ";
    private static final String SUMMARY = "summary";
    private static final String USCORE = "_";
    private static final String COUNTER = "counter";
    private static final String QUANTILE = "quantile";
    private static final String NONE = "none";

    private boolean writeHelpLine;

    public PrometheusExporter() {
        Config config = ConfigProvider.getConfig();
        Optional<Boolean> tmp = config.getOptionalValue(MICROPROFILE_METRICS_OMIT_HELP_LINE, Boolean.class);
        writeHelpLine = !tmp.isPresent() || !tmp.get();
    }

    public StringBuffer exportOneScope(MetricRegistry.Type scope) {

        StringBuffer sb = new StringBuffer();
        getEntriesForScope(scope, sb);

        return sb;
    }

    @Override
    public StringBuffer exportAllScopes() {
        StringBuffer sb = new StringBuffer();

        for (MetricRegistry.Type scope : MetricRegistry.Type.values()) {
            getEntriesForScope(scope, sb);
        }

        return sb;
    }

    @Override
    public StringBuffer exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();

        Metric m = metricMap.get(metricID);

        Map<MetricID, Metric> outMap = new HashMap<>(1);
        outMap.put(metricID, m);

        StringBuffer sb = new StringBuffer();
        exposeEntries(scope, sb, registry, outMap);
        return sb;
    }

    @Override
    public StringBuffer exportMetricsByName(MetricRegistry.Type scope, String name) {
        // TODO method PrometheusExporter.exportMetricsByName
        throw new UnsupportedOperationException("PrometheusExporter.exportMetricsByName not implemented yet");

    }


    @Override
    public String getContentType() {
        return "text/plain";
    }

    private void getEntriesForScope(MetricRegistry.Type scope, StringBuffer sb) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();

        exposeEntries(scope, sb, registry, metricMap);
    }

    private void exposeEntries(MetricRegistry.Type scope, StringBuffer sb, MetricRegistry registry,
                               Map<MetricID, Metric> metricMap) {
        for (Map.Entry<MetricID, Metric> entry : metricMap.entrySet()) {
            String key = entry.getKey().getName();
            Metadata md = registry.getMetadata().get(key);

            if (md == null) {
                throw new IllegalStateException("No entry for " + key + " found");
            }

            Metric metric = entry.getValue();

            StringBuffer metricBuf = new StringBuffer();

            try {
                switch (md.getTypeRaw()) {
                    case GAUGE: {
                        key = getPrometheusMetricName(key);
                        writeHelpLine(metricBuf, scope, key, md, null);
                        writeTypeLine(metricBuf, scope, key, md, null, null);
                        createSimpleValueLine(metricBuf, scope, key, md, metric, null);
                        break;
                    }
                    case COUNTER:
                        String suffix = md.getName().endsWith("_total") ? null : "_total";
                        key = getPrometheusMetricName(key);
                        writeHelpLine(metricBuf, scope, key, md, suffix);
                        writeTypeLine(metricBuf, scope, key, md, suffix, null);
                        createSimpleValueLine(metricBuf, scope, key, md, metric, suffix);
                        break;
                    case CONCURRENT_GAUGE:
                        ConcurrentGauge concurrentGauge = (ConcurrentGauge) metric;
                        writeConcurrentGaugeValues(sb, scope, concurrentGauge, md, key);
                        break;
                    case METERED:
                        Metered meter = (Metered) metric;
                        writeMeterValues(metricBuf, scope, meter, md);
                        break;
                    case TIMER:
                        Timer timer = (Timer) metric;
                        writeTimerValues(metricBuf, scope, timer, md);
                        break;
                    case HISTOGRAM:
                        Histogram histogram = (Histogram) metric;
                        writeHistogramValues(metricBuf, scope, histogram, md);
                        break;
                    default:
                        throw new IllegalArgumentException("Not supported: " + key);
                }
                sb.append(metricBuf);
            } catch (Exception e) {
                log.warn("Unable to export metric " + key, e);
            }
        }
    }

    private void writeTimerValues(StringBuffer sb, MetricRegistry.Type scope, Timer timer, Metadata md) {

        String unit = PrometheusUnit.getBaseUnitAsPrometheusString(md.getUnit());

        String theUnit = unit.equals(NONE) ? "" : USCORE + unit;

        writeMeterRateValues(sb, scope, timer, md);
        Snapshot snapshot = timer.getSnapshot();
        writeSnapshotBasics(sb, scope, md, snapshot, theUnit, true);

        String suffix = USCORE + PrometheusUnit.getBaseUnitAsPrometheusString(md.getUnit());
        writeHelpLine(sb, scope, md.getName(), md, suffix);
        writeTypeLine(sb,scope,md.getName(),md, suffix,SUMMARY);
        writeValueLine(sb,scope,suffix + "_count",timer.getCount(),md, null, false);

        writeSnapshotQuantiles(sb, scope, md, snapshot, theUnit, true);
    }

    private void writeConcurrentGaugeValues(StringBuffer sb, MetricRegistry.Type scope, ConcurrentGauge concurrentGauge, Metadata md, String key) {
        key = getPrometheusMetricName(key);
        writeHelpLine(sb, scope, key, md, "_current");
        writeTypeAndValue(sb, scope, "_current", concurrentGauge.getCount(), GAUGE, md, false);
        writeTypeAndValue(sb, scope, "_max", concurrentGauge.getMax(), GAUGE, md, false);
        writeTypeAndValue(sb, scope, "_min", concurrentGauge.getMin(), GAUGE, md, false);
    }

    private void writeHistogramValues(StringBuffer sb, MetricRegistry.Type scope, Histogram histogram, Metadata md) {

        Snapshot snapshot = histogram.getSnapshot();
        Optional<String> optUnit = md.getUnit();
        String unit = PrometheusUnit.getBaseUnitAsPrometheusString(optUnit);

        String theUnit = unit.equals("none") ? "" : USCORE + unit;

        writeHelpLine(sb, scope, md.getName(), md, SUMMARY);
        writeSnapshotBasics(sb, scope, md, snapshot, theUnit, true);
        writeTypeLine(sb,scope,md.getName(),md, theUnit,SUMMARY);
        writeValueLine(sb,scope,theUnit + "_count",histogram.getCount(),md, null, false);
        writeSnapshotQuantiles(sb, scope, md, snapshot, theUnit, true);
    }


    private void writeSnapshotBasics(StringBuffer sb, MetricRegistry.Type scope, Metadata md, Snapshot snapshot, String unit, boolean performScaling) {

        writeTypeAndValue(sb, scope, "_min" + unit, snapshot.getMin(), GAUGE, md, performScaling);
        writeTypeAndValue(sb, scope, "_max" + unit, snapshot.getMax(), GAUGE, md, performScaling);
        writeTypeAndValue(sb, scope, "_mean" + unit, snapshot.getMean(), GAUGE, md, performScaling);
        writeTypeAndValue(sb, scope, "_stddev" + unit, snapshot.getStdDev(), GAUGE, md, performScaling);
    }

    private void writeSnapshotQuantiles(StringBuffer sb, MetricRegistry.Type scope, Metadata md, Snapshot snapshot, String unit, boolean performScaling) {
        writeValueLine(sb, scope, unit, snapshot.getMedian(), md, new Tag(QUANTILE, "0.5"), performScaling);
        writeValueLine(sb, scope, unit, snapshot.get75thPercentile(), md, new Tag(QUANTILE, "0.75"), performScaling);
        writeValueLine(sb, scope, unit, snapshot.get95thPercentile(), md, new Tag(QUANTILE, "0.95"), performScaling);
        writeValueLine(sb, scope, unit, snapshot.get98thPercentile(), md, new Tag(QUANTILE, "0.98"), performScaling);
        writeValueLine(sb, scope, unit, snapshot.get99thPercentile(), md, new Tag(QUANTILE, "0.99"), performScaling);
        writeValueLine(sb, scope, unit, snapshot.get999thPercentile(), md, new Tag(QUANTILE, "0.999"), performScaling);
    }

    private void writeMeterValues(StringBuffer sb, MetricRegistry.Type scope, Metered metric, Metadata md) {
        writeHelpLine(sb, scope, md.getName(), md, "_total");
        writeTypeAndValue(sb, scope, "_total", metric.getCount(), COUNTER, md, false);
        writeMeterRateValues(sb, scope, metric, md);
    }

    private void writeMeterRateValues(StringBuffer sb, MetricRegistry.Type scope, Metered metric, Metadata md) {
        writeTypeAndValue(sb, scope, "_rate_per_second", metric.getMeanRate(), GAUGE, md, false);
        writeTypeAndValue(sb, scope, "_one_min_rate_per_second", metric.getOneMinuteRate(), GAUGE, md, false);
        writeTypeAndValue(sb, scope, "_five_min_rate_per_second", metric.getFiveMinuteRate(), GAUGE, md, false);
        writeTypeAndValue(sb, scope, "_fifteen_min_rate_per_second", metric.getFifteenMinuteRate(), GAUGE, md, false);
    }

    private void writeTypeAndValue(StringBuffer sb, MetricRegistry.Type scope, String suffix, double valueRaw, String type, Metadata md, boolean performScaling) {
        String key = md.getName();
        writeTypeLine(sb, scope, key, md, suffix, type);
        writeValueLine(sb, scope, suffix, valueRaw, md, null, performScaling);
    }

    private void writeValueLine(StringBuffer sb, MetricRegistry.Type scope, String suffix, double valueRaw, Metadata md) {
        writeValueLine(sb, scope, suffix, valueRaw, md, null);
    }

    private void writeValueLine(StringBuffer sb, MetricRegistry.Type scope, String suffix, double valueRaw, Metadata md, Tag extraTag) {
        writeValueLine(sb, scope, suffix, valueRaw, md, extraTag, true);
    }

    private void writeValueLine(StringBuffer sb,
                                MetricRegistry.Type scope,
                                String suffix,
                                double valueRaw,
                                Metadata md,
                                Tag extraTag,
                                boolean performScaling) {
        String name = md.getName();
        name = getPrometheusMetricName(name);
        fillBaseName(sb, scope, name, suffix);

        // add tags

        Map<String, String> tags = new HashMap<>(); // TODO new HashMap<>(md.getTags());
        if (extraTag != null) {
            tags.put(extraTag.getTagName(), extraTag.getTagValue());
        }
        if (!tags.isEmpty()) {
            addTags(sb, tags);
        }

        sb.append(SPACE);

        Double value;
        if(performScaling) {
            String scaleFrom = "nanoseconds";
            if(md.getTypeRaw() == MetricType.HISTOGRAM)
                // for histograms, internally the data is stored using the metric's unit
                scaleFrom = md.getUnit().orElse(NONE);
            value = PrometheusUnit.scaleToBase(scaleFrom, valueRaw);
        } else {
            value = valueRaw;
        }
        sb.append(value).append(LF);

    }

    private void addTags(StringBuffer sb, Map<String, String> tags) {
        Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator();
        sb.append("{");
        while (iter.hasNext()) {
            Map.Entry<String, String> tag = iter.next();
            sb.append(tag.getKey()).append("=\"").append(tag.getValue()).append("\"");
            if (iter.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("}");
    }

    private void fillBaseName(StringBuffer sb, MetricRegistry.Type scope, String key, String suffix) {
        sb.append(scope.getName().toLowerCase()).append("_").append(key);
        if(suffix != null)
            sb.append(suffix);
    }

    private void writeHelpLine(StringBuffer sb, MetricRegistry.Type scope, String key, Metadata md, String suffix) {
        // Only write this line if we actually have a description in metadata
        if (writeHelpLine && md.getDescription().isPresent()) {
            sb.append("# HELP ");
            getNameWithScopeAndSuffix(sb, scope, key, suffix);
            sb.append(md.getDescription().get());
            sb.append(LF);
        }

    }

    private void writeTypeLine(StringBuffer sb, MetricRegistry.Type scope, String key, Metadata md, String suffix, String typeOverride) {
        sb.append("# TYPE ");
        getNameWithScopeAndSuffix(sb, scope, key, suffix);
        if (typeOverride != null) {
            sb.append(typeOverride);
        } else if (md.getTypeRaw().equals(MetricType.TIMER)) {
            sb.append(SUMMARY);
        } else if (md.getTypeRaw().equals(MetricType.METERED)) {
            sb.append(COUNTER);
        } else {
            sb.append(md.getType());
        }
        sb.append(LF);
    }

    private void getNameWithScopeAndSuffix(StringBuffer sb, MetricRegistry.Type scope, String key, String suffix) {
        sb.append(scope.getName().toLowerCase());
        sb.append('_').append(getPrometheusMetricName(key));
        if (suffix != null) {
            sb.append(suffix);
        }
        sb.append(SPACE);
    }

    private void createSimpleValueLine(StringBuffer sb, MetricRegistry.Type scope, String key, Metadata md, Metric metric, String suffix) {

        // value line
        fillBaseName(sb, scope, key, suffix);
        String unit = PrometheusUnit.getBaseUnitAsPrometheusString(md.getUnit());
        if (!unit.equals(NONE)) { // TODO will this not cause counters to append units? the unit should be part of the suffix two lines earlier
            sb.append(USCORE).append(unit);
        }
        String tags = null; // TODO md.getTagsAsString();
        if (tags != null && !tags.isEmpty()) {            // TODO what with this?
            sb.append('{').append(tags).append('}');
        }

        double valIn;
        if (md.getTypeRaw().equals(MetricType.GAUGE)) {
            Number value1 = (Number) ((Gauge) metric).getValue();
            if (value1 != null) {
                valIn = value1.doubleValue();
            } else {
                log.warn("Value is null for " + key);
                throw new IllegalStateException("Value must not be null for " + key);
            }
        } else {
            valIn = (double) ((Counter) metric).getCount();
        }

        Double value = PrometheusUnit.scaleToBase(md.getUnit().orElse(NONE), valIn);
        sb.append(SPACE).append(value).append(LF);

    }


    static String getPrometheusMetricName(String name) {
        String out = name.replaceAll("[^\\w]+",USCORE);
        out = decamelize(out);
        out = out.replace("__", USCORE);
        out = out.replace(":_", ":");

        return out;
    }

    private static String decamelize(String in) {
        Matcher m = SNAKE_CASE_PATTERN.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, USCORE + m.group().toLowerCase());
        }
        m.appendTail(sb);
        return sb.toString().toLowerCase();
    }
}
