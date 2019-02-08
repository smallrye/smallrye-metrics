package io.smallrye.metrics.setup;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.ExtendedMetadataAndTags;
import io.smallrye.metrics.JmxWorker;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.mbean.MCounterImpl;
import io.smallrye.metrics.mbean.MGaugeImpl;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
        List<ExtendedMetadataAndTags> configs = findMetadata(propertiesFile);

        for (ExtendedMetadataAndTags config : configs) {
            register(registry, config.getMetadata(), config.getTags());
        }
    }

    void register(MetricRegistry registry, ExtendedMetadata config, List<Tag> tags) {
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
            registry.register(config, metric, tags.toArray(new Tag[]{}));
        }
    }

    private List<ExtendedMetadataAndTags> findMetadata(String propertiesFile) throws IOException {
        try (
                InputStream propertiesResource = getResource("/io/smallrye/metrics/" + propertiesFile)
        ) {
            if (propertiesResource == null) {
                return Collections.emptyList();
            }

            List<ExtendedMetadataAndTags> resultList = loadMetadataFromProperties(propertiesResource);

            JmxWorker.instance().expandMultiValueEntries(resultList);

            return resultList;
        }
    }

    List<ExtendedMetadataAndTags> loadMetadataFromProperties(InputStream propertiesResource) throws IOException {
        Properties baseMetricsProps = new Properties();
        baseMetricsProps.load(propertiesResource);

        Map<String, List<MetricProperty>> parsedMetrics = baseMetricsProps.entrySet()
                .stream()
                .map(MetricProperty::new)
                .collect(Collectors.groupingBy(MetricProperty::getMetricName));

        return parsedMetrics.entrySet()
                .stream()
                .map(this::metadataOf)
                .sorted(Comparator.comparing(e -> e.getMetadata().getName()))
                .collect(Collectors.toList());
    }

    private InputStream getResource(String location) {
        InputStream is = getClass().getResourceAsStream(location);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        }
        return is;
    }

    private ExtendedMetadataAndTags metadataOf(Map.Entry<String, List<MetricProperty>> metadataEntry) {
        String name = metadataEntry.getKey();
        Map<String, String> entryProperties = new HashMap<>();
        metadataEntry.getValue()
                .forEach(
                        prop -> entryProperties.put(prop.propertyKey, prop.propertyValue)
                );
        List<Tag> tags = new ArrayList<>();
        if(entryProperties.containsKey("tags")) {
            final String labelDefs[] = entryProperties.get("tags").split(";");
            for (final String labelDef : labelDefs) {
                final String label[] = labelDef.split("=", 2);
                final Tag tag = new Tag(label[0], label[1]);
                tags.add(tag);
            }
        }

        ExtendedMetadata meta = new ExtendedMetadata(name, entryProperties.get("displayName"),
                entryProperties.get("description"),
                metricTypeOf(entryProperties.get("type")),
                entryProperties.get("unit"),
                entryProperties.get("mbean"),
                "true".equalsIgnoreCase(entryProperties.get("multi")));

        return new ExtendedMetadataAndTags(meta, tags);
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
