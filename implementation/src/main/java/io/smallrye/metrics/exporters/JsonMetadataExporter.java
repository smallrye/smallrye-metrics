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
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bob on 1/22/18.
 */
public class JsonMetadataExporter implements Exporter {

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public StringBuffer exportOneScope(MetricRegistry.Type scope) {
        MetricRegistry registry = MetricRegistries.get(scope);
        if (registry == null) {
            return null;
        }

        JsonObject obj = registryJSON(registry);
        return stringify(obj);
    }

    @Override
    public StringBuffer exportAllScopes() {
        JsonObject obj = rootJSON();
        return stringify(obj);
    }

    @Override
    public StringBuffer exportOneMetric(MetricRegistry.Type scope, MetricID metricID) {
        throw new UnsupportedOperationException("Exporting metadata of one metricID is currently not implemented because it is not possible to perform such export according to specification.");
    }

    @Override
    public StringBuffer exportMetricsByName(MetricRegistry.Type scope, String name) {
        MetricRegistry registry = MetricRegistries.get(scope);
        if (registry == null) {
            return null;
        }

        Metadata metadata = registry.getMetadata().get(name);

        if (metadata == null) {
            return null;
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        // TODO find a way to append the set of known sets of tags for this metricName
        metricJSON(builder, name, metadata);
        return stringify(builder.build());

    }

    private static final Map<String, ?> JSON_CONFIG = new HashMap<String, Object>() {{
        put(JsonGenerator.PRETTY_PRINTING, true);
    }};

    StringBuffer stringify(JsonObject obj) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = Json.createWriterFactory(JSON_CONFIG).createWriter(out)) {
            writer.writeObject(obj);
        }
        return out.getBuffer();
    }


    private JsonObject rootJSON() {
        JsonObjectBuilder root = Json.createObjectBuilder();

        root.add("base", registryJSON(MetricRegistries.get(MetricRegistry.Type.BASE)));
        root.add("vendor", registryJSON(MetricRegistries.get(MetricRegistry.Type.VENDOR)));
        root.add("application", registryJSON(MetricRegistries.get(MetricRegistry.Type.APPLICATION)));

        return root.build();
    }

    private JsonObject registryJSON(MetricRegistry registry) {
        JsonObjectBuilder registryJSON = Json.createObjectBuilder();
        Map<String, Metadata> metrics = registry.getMetadata();
        metrics.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> metricJSON(registryJSON, e.getKey(), e.getValue()));
        return registryJSON.build();
    }

    private void metricJSON(JsonObjectBuilder registryJSON, String name, Metadata metric) {
        registryJSON.add(name, metricJSON(metric));
    }

    private JsonObject metricJSON(Metadata metadata) {
        JsonObjectBuilder obj = Json.createObjectBuilder();

        if (metadata.getUnit().isPresent()) {
            obj.add("unit", metadata.getUnit().get());
        }
        if (metadata.getType() != null) {
            obj.add("type", metadata.getType());
        }
        if (metadata.getDescription().isPresent()) {
            obj.add("description", metadata.getDescription().get());
        }
        if (metadata.getDisplayName() != null) {
            obj.add("displayName", metadata.getDisplayName());
        }
        // TODO what with this?
/*        if (metadata.getTagsAsString() != null) {
            //obj.add("tags", metadata.getTagsAsString());
            String str = metadata.getTags().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
            obj.add("tags", str);
        }*/

        return obj.build();
    }
}
