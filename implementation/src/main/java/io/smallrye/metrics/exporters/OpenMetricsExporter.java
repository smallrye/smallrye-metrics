package io.smallrye.metrics.exporters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.smallrye.metrics.setup.MPPrometheusMeterRegistry;

public class OpenMetricsExporter implements Exporter {

    private final List<MeterRegistry> prometheusRegistryList;

    public OpenMetricsExporter() {
        prometheusRegistryList = Metrics.globalRegistry.getRegistries().stream()
                .filter(registry -> registry instanceof MPPrometheusMeterRegistry).collect(Collectors.toList());

        if (prometheusRegistryList == null || prometheusRegistryList.size() == 0) {
            throw new IllegalStateException("Prometheus registry was not found in the global registry");
            //TODO:  logging
        }

    }

    @Override
    public String exportAllScopes() {
        StringBuilder sb = new StringBuilder();
        for (MeterRegistry meterRegistry : prometheusRegistryList) {
            PrometheusMeterRegistry promMeterRegistry = (PrometheusMeterRegistry) meterRegistry;
            //strip "# EOF"
            sb.append(promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100).replaceFirst("\r?\n?# EOF", ""));
        }
        return sb.toString();
    }

    @Override
    public String exportOneScope(MetricRegistry.Type scope) {
        for (MeterRegistry meterRegistry : prometheusRegistryList) {
            MPPrometheusMeterRegistry promMeterRegistry = (MPPrometheusMeterRegistry) meterRegistry;
            if (promMeterRegistry.getType() == scope) {
                return promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100).replaceFirst("\r?\n?# EOF", "");
            }
        }
        return null; //FIXME: throw exception, logging?
    }

    /*
     * Not used.
     */
    @Override
    public String exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String exportMetricsByName(MetricRegistry.Type scope, String name) {

        /**
         * FIXME: Refactor
         * Get Meter Registry from Metric Registry
         * Search Meter registry for name
         * - unit? (some meter might have unit, some might not, used in calculation of meter names). [return set]
         * - Meter specific suffixes [return set]
         * Create set w/ Prometheus Name + <unit> + specific suffixes
         * ^*combo of with unit and without unit
         * Scrape PMR with set
         */
        for (MeterRegistry meterRegistry : prometheusRegistryList) {
            MPPrometheusMeterRegistry promMeterRegistry = (MPPrometheusMeterRegistry) meterRegistry;
            if (promMeterRegistry.getType() == scope) {
                Set<String> unitTypesSet = new HashSet<String>();
                unitTypesSet.add("");
                Set<String> meterSuffixSet = new HashSet<String>();
                meterSuffixSet.add("");

                for (Meter m : meterRegistry.find(name).meters()) {
                    unitTypesSet.add(m.getId().getBaseUnit());
                    resolveMeterSuffixes(meterSuffixSet, m.getId().getType());
                }

                Set<String> scrapeMeterNames = calculateMeterNamesToScrape(name, meterSuffixSet, unitTypesSet);

                //Strip #EOF from output
                return promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100, scrapeMeterNames)
                        .replaceFirst("\r?\n?# EOF", "");
            }
        }
        return null;
    }

    /**
     * Since this implementation uses Micrometer, it prefixes any metric name that does not start with a letter with
     * "m_". Since we need to be able to query by a specific metric name we need to "format" the metric name and to be
     * similar to the output formatted by the Prometheus client. This will be used with the pmr.scrape(String contenttype,
     * Set<String> names)
     * to get the metric we want.
     *
     * @param input
     * @return
     */
    private String resolvePrometheusName(String input) {
        String output = input;

        //Change other special characters to underscore - Use to convert double underscores to single underscore
        output = output.replaceAll("[-+.!?@#$%^&*`'\\s]+", "_");

        //Match with Prometheus simple client formatter where it appends "m_" if it starts with a number
        if (output.matches("^[0-9]+.*")) {
            output = "m_" + output;
        }

        //Match with Prometheus simple client formatter where it appends "m_" if it starts with a "underscore"
        if (output.matches("^_+.*")) {
            output = "m_" + output;
        }

        //non-ascii characters to "" -- does this affect other languages?
        output = output.replaceAll("[^A-Za-z0-9_]", "");

        return output;
    }

    private void resolveMeterSuffixes(Set<String> set, Type inputType) {
        switch (inputType) {
            case COUNTER:
                set.add("_total");
                break;
            case DISTRIBUTION_SUMMARY:
            case TIMER:
            case LONG_TASK_TIMER:
                set.add("_count");
                set.add("_sum");
                set.add("_max");
                break;
            default:
                //TODO: error/warnning/exception statement
                break;
        }
    }

    private Set<String> calculateMeterNamesToScrape(String name, Set<String> meterSuffixSet, Set<String> unitTypeSet) {
        String promName = resolvePrometheusName(name);
        Set<String> retSet = new HashSet<String>();
        for (String unit : unitTypeSet) {
            for (String suffix : meterSuffixSet) {
                retSet.add(promName + unit + suffix);
            }
        }
        return retSet;
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

}
