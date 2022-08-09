package io.smallrye.metrics.exporters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.smallrye.metrics.setup.MPPrometheusMeterRegistry;

public class PrometheusMetricsExporter implements Exporter {

    private final List<MeterRegistry> prometheusRegistryList;

    public PrometheusMetricsExporter() {
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
            String scraped = promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_004).replaceFirst("# EOF\r?\n?", "");
            sb.append(scraped);
        }
        return sb.toString();
    }

    @Override
    public String exportOneScope(String scope) {

        for (MeterRegistry meterRegistry : prometheusRegistryList) {
            MPPrometheusMeterRegistry promMeterRegistry = (MPPrometheusMeterRegistry) meterRegistry;
            if (promMeterRegistry.getScope().equals(scope)) {
                return promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_004).replaceFirst("# EOF\r?\n?", "");
            }
        }
        return null; //FIXME: throw exception, logging?
    }

    @Override
    public String exportMetricsByName(String scope, String name) {

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

            if (promMeterRegistry.getScope().equals(scope)) {
                Set<String> unitTypesSet = new HashSet<String>();
                unitTypesSet.add("");
                Set<String> meterSuffixSet = new HashSet<String>();
                meterSuffixSet.add("");

                for (Meter m : meterRegistry.find(name).meters()) {
                    unitTypesSet.add("_" + m.getId().getBaseUnit());
                    resolveMeterSuffixes(meterSuffixSet, m.getId().getType());
                }

                Set<String> scrapeMeterNames = calculateMeterNamesToScrape(name, meterSuffixSet, unitTypesSet);
                //Strip #EOF from output
                return promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_004, scrapeMeterNames)
                        .replaceFirst("\r?\n?# EOF", "");
            }
        }
        return null;
    }

    @Override
    public String exportOneMetricAcrossScopes(String name) {
        StringBuilder sb = new StringBuilder();
        for (MeterRegistry meterRegistry : prometheusRegistryList) {
            PrometheusMeterRegistry promMeterRegistry = (PrometheusMeterRegistry) meterRegistry;
            /*
             * For each Prometheus registry found:
             * 1. Calculate potential formatted names
             * 2. Scrape with Set of names
             * 3. Append to StringBuilder
             * 4. return.
             * Note: See above's exportMetricsByName if we need
             * to refactor for a better way.
             */
            Set<String> unitTypesSet = new HashSet<String>();
            unitTypesSet.add("");
            Set<String> meterSuffixSet = new HashSet<String>();
            meterSuffixSet.add("");

            for (Meter m : meterRegistry.find(name).meters()) {
                unitTypesSet.add("_" + m.getId().getBaseUnit());
                resolveMeterSuffixes(meterSuffixSet, m.getId().getType());
            }

            Set<String> scrapeMeterNames = calculateMeterNamesToScrape(name, meterSuffixSet, unitTypesSet);
            //Strip #EOF from output
            String output = promMeterRegistry.scrape(TextFormat.CONTENT_TYPE_004, scrapeMeterNames)
                    .replaceFirst("\r?\n?# EOF", "");

            sb.append(output);
        }
        return sb.toString();
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
