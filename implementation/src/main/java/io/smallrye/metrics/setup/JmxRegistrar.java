package io.smallrye.metrics.setup;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.mbean.MCounterImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;


/**
 * Created by bob on 1/22/18.
 * Modified to work on a single properties file by Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 */
public class JmxRegistrar {

    public void init() throws IOException {
        register("base-metrics.properties", MetricRegistries.get(MetricRegistry.Type.BASE));
        register("vendor-metrics.properties", MetricRegistries.get(MetricRegistry.Type.VENDOR));
    }

    private void register(String propertiesFile, MetricRegistry registry) throws IOException {
        List<ExtendedMetadata> configs = findMetadata(propertiesFile);

        for (ExtendedMetadata config : configs) {
            register(registry, config);
        }
    }

    void register(MetricRegistry registry, ExtendedMetadata config) {
        Metric metric = null;
        switch (config.getTypeRaw()) {
            case COUNTER:
                metric = new MCounterImpl(JmxWorker.instance(), config.getMbean());
                break;
            case GAUGE:
                metric = new MGaugeImpl(JmxWorker.instance(), config.getMbean());
                break;
        }

        if (metric != null) {
            registry.register(config, metric);
        }
    }

    private List<ExtendedMetadata> findMetadata(String propertiesFile) throws IOException {
        try (
                InputStream propertiesResource = getResource("/io/smallrye/metrics/" + propertiesFile)
        ) {
            if (propertiesResource == null) {
                return Collections.emptyList();
            }

            List<ExtendedMetadata> resultList = loadMetadataFromProperties(propertiesResource);

            JmxWorker.instance().expandMultiValueEntries(resultList);

            return resultList;
        }
    }

    List<ExtendedMetadata> loadMetadataFromProperties(InputStream propertiesResource) throws IOException {
        Properties baseMetricsProps = new Properties();
        baseMetricsProps.load(propertiesResource);

        Map<String, List<MetricProperty>> parsedMetrics = baseMetricsProps.entrySet()
                .stream()
                .map(MetricProperty::new)
                .collect(Collectors.groupingBy(MetricProperty::getMetricName));

        return parsedMetrics.entrySet()
                .stream()
                .map(this::metadataOf)
                .sorted(Comparator.comparing(e -> e.getName()))
                .collect(Collectors.toList());
    }

    private InputStream getResource(String location) {
        InputStream is = getClass().getResourceAsStream(location);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        }
        return is;
    }

    private ExtendedMetadata metadataOf(Map.Entry<String, List<MetricProperty>> metadataEntry) {
        String name = metadataEntry.getKey();
        Map<String, String> entryProperties = new HashMap<>();
        metadataEntry.getValue()
                .forEach(
                        prop -> entryProperties.put(prop.propertyKey, prop.propertyValue)
                );
        ExtendedMetadata meta = new ExtendedMetadata(name, entryProperties.get("displayName"),
                                                     entryProperties.get("description"),
                                                     metricTypeOf(entryProperties.get("type")),entryProperties.get(
                                                         "unit"));
        meta.setMbean(entryProperties.get("mbean"));
        meta.setMulti("true".equalsIgnoreCase(entryProperties.get("multi")));
        if(entryProperties.containsKey("tags")) {

        	final String labelDefs[] = entryProperties.get("tags").split(";");
        	for (final String labelDef : labelDefs) {
        		final String label[] = labelDef.split("=", 2);
				meta.getTags().put(label[0], label[1]);
			}
        	
        }
        return meta;
    }

    private static class MetricProperty {
        MetricProperty(Map.Entry<Object, Object> keyValue) {
            String key = (String) keyValue.getKey();
            int propertyIdEnd = key.lastIndexOf('.');
            metricName = key.substring(0, propertyIdEnd);
            propertyKey = key.substring(propertyIdEnd + 1);
            propertyValue = (String) keyValue.getValue();
        }

        String metricName;
        String propertyKey;
        String propertyValue;

        String getMetricName() {
            return metricName;
        }
    }

    MetricType metricTypeOf(String type) {
        return MetricType.valueOf(type.toUpperCase());
    }

}
