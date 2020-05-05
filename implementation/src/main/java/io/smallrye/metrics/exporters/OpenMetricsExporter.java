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

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.SmallRyeMetricsLogging;

/**
 * Export data in OpenMetrics text format
 *
 * @author Heiko W. Rupp
 */
public class OpenMetricsExporter implements Exporter {

    // This allows to suppress the (noisy) # HELP line
    private static final String MICROPROFILE_METRICS_OMIT_HELP_LINE = "microprofile.metrics.omitHelpLine";
    // Use a prefix to provide the MicroProfile Metrics scope. If false, the scope will be added to the metrics tag
    // with the key "microprofile_scope" instead.
    public static final String SMALLRYE_METRICS_USE_PREFIX_FOR_SCOPE = "smallrye.metrics.usePrefixForScope";

    private static final String LF = "\n";
    private static final String GAUGE = "gauge";
    private static final String SPACE = " ";
    private static final String SUMMARY = "summary";
    private static final String USCORE = "_";
    private static final String COUNTER = "counter";
    private static final String QUANTILE = "quantile";
    private static final String NONE = "none";

    private boolean writeHelpLine;
    private boolean usePrefixForScope;

    // names of metrics for which we have already exported TYPE and HELP lines within one scope
    // this is to prevent writing them multiple times for the same metric name
    // this should be initialized to an empty map during start of an export and cleared after the export is finished
    private ThreadLocal<Set<String>> alreadyExportedNames = new ThreadLocal<>();

    public OpenMetricsExporter() {
        try {
            Config config = ConfigProvider.getConfig();
            Optional<Boolean> tmp = config.getOptionalValue(MICROPROFILE_METRICS_OMIT_HELP_LINE, Boolean.class);
            usePrefixForScope = config.getOptionalValue(SMALLRYE_METRICS_USE_PREFIX_FOR_SCOPE, Boolean.class).orElse(true);
            writeHelpLine = !tmp.isPresent() || !tmp.get();
        } catch (IllegalStateException | ExceptionInInitializerError | NoClassDefFoundError t) {
            // MP Config implementation is probably not available. Resort to default configuration.
            usePrefixForScope = true;
            writeHelpLine = true;
        }

    }

    @Override
    public StringBuilder exportOneScope(MetricRegistry.Type scope) {
        alreadyExportedNames.set(new HashSet<>());
        StringBuilder sb = new StringBuilder();
        getEntriesForScope(scope, sb);
        alreadyExportedNames.set(null);
        return sb;
    }

    @Override
    public StringBuilder exportAllScopes() {
        StringBuilder sb = new StringBuilder();

        for (MetricRegistry.Type scope : MetricRegistry.Type.values()) {
            alreadyExportedNames.set(new HashSet<>());
            getEntriesForScope(scope, sb);
            alreadyExportedNames.set(null);
        }

        return sb;
    }

    @Override
    public StringBuilder exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        alreadyExportedNames.set(new HashSet<>());
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();

        Metric m = metricMap.get(metricID);

        Map<MetricID, Metric> outMap = new HashMap<>(1);
        outMap.put(metricID, m);

        StringBuilder sb = new StringBuilder();
        exposeEntries(scope, sb, registry, outMap);
        alreadyExportedNames.set(null);
        return sb;
    }

    @Override
    public StringBuilder exportMetricsByName(MetricRegistry.Type scope, String name) {
        alreadyExportedNames.set(new HashSet<>());
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricsToExport = registry.getMetrics()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().getName().equals(name))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        StringBuilder sb = new StringBuilder();
        exposeEntries(scope, sb, registry, metricsToExport);
        alreadyExportedNames.set(null);
        return sb;
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    private void getEntriesForScope(MetricRegistry.Type scope, StringBuilder sb) {
        MetricRegistry registry = MetricRegistries.get(scope);
        Map<MetricID, Metric> metricMap = registry.getMetrics();

        exposeEntries(scope, sb, registry, new TreeMap<>(metricMap));
    }

    private void exposeEntries(MetricRegistry.Type scope, StringBuilder sb, MetricRegistry registry,
            Map<MetricID, Metric> metricMap) {
        Map<String, Metadata> metadataMap = registry.getMetadata();
        for (Map.Entry<MetricID, Metric> entry : metricMap.entrySet()) {
            String key = entry.getKey().getName();
            Metadata md = metadataMap.get(key);

            if (md == null) {
                throw new IllegalStateException("No entry for " + key + " found");
            }

            Metric metric = entry.getValue();
            final Map<String, String> tagsMap = entry.getKey().getTags();
            StringBuilder metricBuf = new StringBuilder();

            try {
                switch (md.getTypeRaw()) {
                    case GAUGE: {
                        String unitSuffix = null;
                        String unit;
                        String keyOverride = getOpenMetricsKeyOverride(md);
                        if (keyOverride != null) {
                            key = keyOverride;
                        } else {
                            key = getOpenMetricsMetricName(key);
                            unit = OpenMetricsUnit.getBaseUnitAsOpenMetricsString(md.unit());
                            if (!unit.equals(NONE)) {
                                unitSuffix = "_" + unit;
                            }
                        }
                        writeHelpLine(metricBuf, scope, key, md, unitSuffix);
                        writeTypeLine(metricBuf, scope, key, md, unitSuffix, null);
                        createSimpleValueLine(metricBuf, scope, key, md, metric, null, tagsMap);
                        break;
                    }
                    case COUNTER:
                        String suffix;

                        String keyOverride = getOpenMetricsKeyOverride(md);
                        if (keyOverride != null) {
                            key = keyOverride;
                            suffix = null;
                        } else {
                            key = getOpenMetricsMetricName(key);
                            suffix = key.endsWith("_total") ? null : "_total";
                        }
                        writeHelpLine(metricBuf, scope, key, md, suffix);
                        writeTypeLine(metricBuf, scope, key, md, suffix, null);
                        createSimpleValueLine(metricBuf, scope, key, md, metric, suffix, tagsMap);
                        break;
                    case CONCURRENT_GAUGE:
                        ConcurrentGauge concurrentGauge = (ConcurrentGauge) metric;
                        writeConcurrentGaugeValues(sb, scope, concurrentGauge, md, key, tagsMap);
                        break;
                    case METERED:
                        Metered meter = (Metered) metric;
                        writeMeterValues(metricBuf, scope, meter, md, tagsMap);
                        break;
                    case TIMER:
                        Timer timer = (Timer) metric;
                        writeTimerValues(metricBuf, scope, timer, md, tagsMap);
                        break;
                    case HISTOGRAM:
                        Histogram histogram = (Histogram) metric;
                        writeHistogramValues(metricBuf, scope, histogram, md, tagsMap);
                        break;
                    case SIMPLE_TIMER:
                        SimpleTimer simpleTimer = (SimpleTimer) metric;
                        writeSimpleTimerValues(metricBuf, scope, simpleTimer, md, tagsMap);
                        break;
                    default:
                        throw new IllegalArgumentException("Not supported: " + key);
                }
                sb.append(metricBuf);
                alreadyExportedNames.get().add(md.getName());
            } catch (Exception e) {
                SmallRyeMetricsLogging.log.unableToExport(key, e);
            }
        }
    }

    private void writeTimerValues(StringBuilder sb, MetricRegistry.Type scope, Timer timer, Metadata md,
            Map<String, String> tags) {

        String unit = OpenMetricsUnit.getBaseUnitAsOpenMetricsString(md.unit());
        if (unit.equals(NONE))
            unit = "seconds";

        String theUnit = USCORE + unit;

        writeMeterRateValues(sb, scope, timer, md, tags);
        Snapshot snapshot = timer.getSnapshot();
        writeSnapshotBasics(sb, scope, md, snapshot, theUnit, true, tags);

        writeHelpLine(sb, scope, md.getName(), md, theUnit);
        writeTypeLine(sb, scope, md.getName(), md, theUnit, SUMMARY);
        writeValueLine(sb, scope, theUnit + "_count", timer.getCount(), md, tags, false);
        writeTypeAndValue(sb, scope, "_elapsedTime" + theUnit, timer.getElapsedTime().toNanos(), GAUGE, md, true, tags);

        writeSnapshotQuantiles(sb, scope, md, snapshot, theUnit, true, tags);
    }

    private void writeSimpleTimerValues(StringBuilder sb, MetricRegistry.Type scope, SimpleTimer simpleTimer, Metadata md,
            Map<String, String> tags) {
        String unit = OpenMetricsUnit.getBaseUnitAsOpenMetricsString(md.unit());
        if (unit.equals(NONE))
            unit = "seconds";

        String theUnit = USCORE + unit;

        // 'total' value plus the help line
        writeHelpLine(sb, scope, md.getName(), md, "_total");
        writeTypeAndValue(sb, scope, "_total", simpleTimer.getCount(), COUNTER, md, false, tags);
        writeTypeAndValue(sb, scope, "_elapsedTime" + theUnit, simpleTimer.getElapsedTime().toNanos(), GAUGE, md, true, tags);
        Duration min = simpleTimer.getMinTimeDuration();
        Duration max = simpleTimer.getMaxTimeDuration();
        if (min != null) {
            writeTypeAndValue(sb, scope, "_minTimeDuration" + theUnit, min.toNanos(), GAUGE, md, true, tags);
        } else {
            writeTypeAndValue(sb, scope, "_minTimeDuration" + theUnit, Double.NaN, GAUGE, md, true, tags);
        }
        if (max != null) {
            writeTypeAndValue(sb, scope, "_maxTimeDuration" + theUnit, max.toNanos(), GAUGE, md, true, tags);
        } else {
            writeTypeAndValue(sb, scope, "_maxTimeDuration" + theUnit, Double.NaN, GAUGE, md, true, tags);
        }

    }

    private void writeConcurrentGaugeValues(StringBuilder sb, MetricRegistry.Type scope, ConcurrentGauge concurrentGauge,
            Metadata md, String key, Map<String, String> tags) {
        key = getOpenMetricsMetricName(key);
        writeHelpLine(sb, scope, key, md, "_current");
        writeTypeAndValue(sb, scope, "_current", concurrentGauge.getCount(), GAUGE, md, false, tags);
        writeTypeAndValue(sb, scope, "_max", concurrentGauge.getMax(), GAUGE, md, false, tags);
        writeTypeAndValue(sb, scope, "_min", concurrentGauge.getMin(), GAUGE, md, false, tags);
    }

    private void writeHistogramValues(StringBuilder sb, MetricRegistry.Type scope, Histogram histogram, Metadata md,
            Map<String, String> tags) {

        Snapshot snapshot = histogram.getSnapshot();
        Optional<String> optUnit = md.unit();
        String unit = OpenMetricsUnit.getBaseUnitAsOpenMetricsString(optUnit);

        String theUnit = unit.equals("none") ? "" : USCORE + unit;

        writeHelpLine(sb, scope, md.getName(), md, theUnit);
        writeSnapshotBasics(sb, scope, md, snapshot, theUnit, true, tags);
        writeTypeLine(sb, scope, md.getName(), md, theUnit, SUMMARY);
        writeValueLine(sb, scope, theUnit + "_count", histogram.getCount(), md, tags, false);
        writeSnapshotQuantiles(sb, scope, md, snapshot, theUnit, true, tags);
    }

    private void writeSnapshotBasics(StringBuilder sb, MetricRegistry.Type scope, Metadata md, Snapshot snapshot, String unit,
            boolean performScaling, Map<String, String> tags) {

        writeTypeAndValue(sb, scope, "_min" + unit, snapshot.getMin(), GAUGE, md, performScaling, tags);
        writeTypeAndValue(sb, scope, "_max" + unit, snapshot.getMax(), GAUGE, md, performScaling, tags);
        writeTypeAndValue(sb, scope, "_mean" + unit, snapshot.getMean(), GAUGE, md, performScaling, tags);
        writeTypeAndValue(sb, scope, "_stddev" + unit, snapshot.getStdDev(), GAUGE, md, performScaling, tags);
    }

    private void writeSnapshotQuantiles(StringBuilder sb, MetricRegistry.Type scope, Metadata md, Snapshot snapshot,
            String unit,
            boolean performScaling, Map<String, String> tags) {
        Map<String, String> map = copyMap(tags);
        map.put(QUANTILE, "0.5");
        writeValueLine(sb, scope, unit, snapshot.getMedian(), md, map, performScaling);
        map.put(QUANTILE, "0.75");
        writeValueLine(sb, scope, unit, snapshot.get75thPercentile(), md, map, performScaling);
        map.put(QUANTILE, "0.95");
        writeValueLine(sb, scope, unit, snapshot.get95thPercentile(), md, map, performScaling);
        map.put(QUANTILE, "0.98");
        writeValueLine(sb, scope, unit, snapshot.get98thPercentile(), md, map, performScaling);
        map.put(QUANTILE, "0.99");
        writeValueLine(sb, scope, unit, snapshot.get99thPercentile(), md, map, performScaling);
        map.put(QUANTILE, "0.999");
        writeValueLine(sb, scope, unit, snapshot.get999thPercentile(), md, map, performScaling);
    }

    private void writeMeterValues(StringBuilder sb, MetricRegistry.Type scope, Metered metric, Metadata md,
            Map<String, String> tags) {
        writeHelpLine(sb, scope, md.getName(), md, "_total");
        writeTypeAndValue(sb, scope, "_total", metric.getCount(), COUNTER, md, false, tags);
        writeMeterRateValues(sb, scope, metric, md, tags);
    }

    private void writeMeterRateValues(StringBuilder sb, MetricRegistry.Type scope, Metered metric, Metadata md,
            Map<String, String> tags) {
        writeTypeAndValue(sb, scope, "_rate_per_second", metric.getMeanRate(), GAUGE, md, false, tags);
        writeTypeAndValue(sb, scope, "_one_min_rate_per_second", metric.getOneMinuteRate(), GAUGE, md, false, tags);
        writeTypeAndValue(sb, scope, "_five_min_rate_per_second", metric.getFiveMinuteRate(), GAUGE, md, false, tags);
        writeTypeAndValue(sb, scope, "_fifteen_min_rate_per_second", metric.getFifteenMinuteRate(), GAUGE, md, false, tags);
    }

    private void writeTypeAndValue(StringBuilder sb, MetricRegistry.Type scope, String suffix, double valueRaw, String type,
            Metadata md, boolean performScaling, Map<String, String> tags) {
        String key = md.getName();
        writeTypeLine(sb, scope, key, md, suffix, type);
        writeValueLine(sb, scope, suffix, valueRaw, md, tags, performScaling);
    }

    private void writeValueLine(StringBuilder sb, MetricRegistry.Type scope, String suffix, double valueRaw, Metadata md) {
        writeValueLine(sb, scope, suffix, valueRaw, md, null);
    }

    private void writeValueLine(StringBuilder sb, MetricRegistry.Type scope, String suffix, double valueRaw, Metadata md,
            Map<String, String> tags) {
        writeValueLine(sb, scope, suffix, valueRaw, md, tags, true);
    }

    private void writeValueLine(StringBuilder sb,
            MetricRegistry.Type scope,
            String suffix,
            double valueRaw,
            Metadata md,
            Map<String, String> tags,
            boolean performScaling) {
        String name = md.getName();
        name = getOpenMetricsMetricName(name);
        fillBaseName(sb, scope, name, suffix, md);

        // add tags

        if (tags != null) {
            addTags(sb, tags, scope, md);
        }

        sb.append(SPACE);

        Double value;
        if (performScaling) {
            String scaleFrom = "nanoseconds";
            if (md.getTypeRaw() == MetricType.HISTOGRAM)
                // for histograms, internally the data is stored using the metric's unit
                scaleFrom = md.unit().orElse(NONE);
            value = OpenMetricsUnit.scaleToBase(scaleFrom, valueRaw);
        } else {
            value = valueRaw;
        }
        sb.append(value).append(LF);

    }

    private void addTags(StringBuilder sb, Map<String, String> tags, MetricRegistry.Type scope, Metadata metadata) {
        if (tags == null || tags.isEmpty()) {
            // always add the microprofile_scope even if there are no other tags
            if (writeScopeInTag(metadata)) {
                sb.append("{microprofile_scope=\"" + scope.getName().toLowerCase() + "\"}");
            }
            return;
        } else {
            Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator();
            sb.append("{");
            while (iter.hasNext()) {
                Map.Entry<String, String> tag = iter.next();
                sb.append(tag.getKey()).append("=\"").append(quoteValue(tag.getValue())).append("\"");
                if (iter.hasNext()) {
                    sb.append(",");
                }
            }
            // append the microprofile_scope after other tags
            if (writeScopeInTag(metadata)) {
                sb.append(",microprofile_scope=\"" + scope.getName().toLowerCase() + "\"");
            }

            sb.append("}");
        }
    }

    private <K, V> Map<K, V> copyMap(Map<K, V> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void fillBaseName(StringBuilder sb, MetricRegistry.Type scope, String key, String suffix, Metadata metadata) {
        if (writeScopeInPrefix(metadata)) {
            sb.append(scope.getName().toLowerCase()).append("_");
        }
        sb.append(key);
        if (suffix != null)
            sb.append(suffix);
    }

    private void writeHelpLine(final StringBuilder sb, MetricRegistry.Type scope, String key, Metadata md, String suffix) {
        // Only write this line if we actually have a description in metadata
        Optional<String> description = md.description();
        if (writeHelpLine && description.filter(s -> !s.isEmpty()).isPresent()
                && !alreadyExportedNames.get().contains(md.getName())) {
            sb.append("# HELP ");
            getNameWithScopeAndSuffix(sb, scope, key, suffix, md);
            sb.append(quoteHelpText(description.get()));
            sb.append(LF);
        }

    }

    private void writeTypeLine(StringBuilder sb, MetricRegistry.Type scope, String key, Metadata md, String suffix,
            String typeOverride) {
        if (!alreadyExportedNames.get().contains(md.getName())) {
            sb.append("# TYPE ");
            getNameWithScopeAndSuffix(sb, scope, key, suffix, md);
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
    }

    private void getNameWithScopeAndSuffix(StringBuilder sb, MetricRegistry.Type scope, String key, String suffix,
            Metadata metadata) {
        if (writeScopeInPrefix(metadata)) {
            sb.append(scope.getName().toLowerCase()).append('_');
        }
        sb.append(getOpenMetricsMetricName(key));
        if (suffix != null) {
            sb.append(suffix);
        }
        sb.append(SPACE);
    }

    private void createSimpleValueLine(StringBuilder sb, MetricRegistry.Type scope, String key, Metadata md, Metric metric,
            String suffix, Map<String, String> tags) {

        // value line
        fillBaseName(sb, scope, key, suffix, md);
        // append the base unit only in case that the key wasn't overridden
        if (getOpenMetricsKeyOverride(md) == null) {
            String unit = OpenMetricsUnit.getBaseUnitAsOpenMetricsString(md.unit());
            if (!unit.equals(NONE)) {
                sb.append(USCORE).append(unit);
            }
        }

        addTags(sb, tags, scope, md);

        double valIn;
        if (md.getTypeRaw().equals(MetricType.GAUGE)) {
            Number value1 = (Number) ((Gauge) metric).getValue();
            if (value1 != null) {
                valIn = value1.doubleValue();
            } else {
                valIn = Double.NaN;
            }
        } else {
            valIn = (double) ((Counter) metric).getCount();
        }

        Double value = OpenMetricsUnit.scaleToBase(md.unit().orElse(NONE), valIn);
        sb.append(SPACE).append(value).append(LF);

    }

    static String getOpenMetricsMetricName(String name) {
        String out = name;
        out = out.replace("__", USCORE);
        out = out.replaceAll("[^\\w]", USCORE);
        return out;
    }

    private boolean writeScopeInPrefix(Metadata metadata) {
        if (metadata instanceof ExtendedMetadata) {
            ExtendedMetadata extendedMetadata = (ExtendedMetadata) metadata;
            if (extendedMetadata.isSkipsScopeInOpenMetricsExportCompletely())
                return false;
            return extendedMetadata.prependsScopeToOpenMetricsName().orElse(usePrefixForScope);
        } else {
            return usePrefixForScope;
        }
    }

    private boolean writeScopeInTag(Metadata metadata) {
        if (metadata instanceof ExtendedMetadata) {
            ExtendedMetadata extendedMetadata = (ExtendedMetadata) metadata;
            if (extendedMetadata.isSkipsScopeInOpenMetricsExportCompletely())
                return false;
            if (extendedMetadata.prependsScopeToOpenMetricsName().isPresent())
                return !extendedMetadata.prependsScopeToOpenMetricsName().get();
        }
        return !usePrefixForScope;
    }

    public static String quoteHelpText(String value) {
        return value
                // replace \ with \\, unless it is followed by n, in which case it is a newline character, which should not be changed
                .replaceAll("\\\\([^n])", "\\\\\\\\$1")
                // replace \ at the end of the value with \\
                .replaceAll("\\\\$", "\\\\\\\\");
    }

    public static String quoteValue(String value) {
        return value
                // replace newline characters with a literal \n
                .replaceAll("\\n", "\\\\n")
                // replace \ with \\, unless it is followed by n (which means it is an already escaped newline character from the previous step)
                .replaceAll("\\\\([^n])", "\\\\\\\\$1")
                // replace " with \"
                .replaceAll("\"", "\\\\\"")
                // replace \ at the end of the value with \\
                .replaceAll("\\\\$", "\\\\\\\\");
    }

    private static String getOpenMetricsKeyOverride(Metadata md) {
        if (md instanceof ExtendedMetadata && ((ExtendedMetadata) md).getOpenMetricsKeyOverride().isPresent()) {
            return ((ExtendedMetadata) md).getOpenMetricsKeyOverride().get();
        } else {
            return null;
        }
    }

}
