/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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

import static org.junit.Assert.assertEquals;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.After;
import org.junit.Test;

import io.smallrye.metrics.MetricRegistries;

public class JsonMetadataExporterTest {

    @After
    public void cleanupApplicationMetrics() {
        MetricRegistries.get(MetricRegistry.Type.APPLICATION).removeMatching(MetricFilter.ALL);
    }

    @Test
    public void exportByMetricNameWithOneMetricSingleTag() {
        JsonMetadataExporter exporter = new JsonMetadataExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        applicationRegistry.counter("counter1", new Tag("key1", "value1"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();

        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();
        JsonArray outerTagsArray = json.getJsonObject("counter1").getJsonArray("tags");
        assertEquals(1, outerTagsArray.size());
        JsonArray innerArray = outerTagsArray.getJsonArray(0);
        assertEquals(1, innerArray.size());
        assertEquals("key1=value1", innerArray.getString(0));
    }

    @Test
    public void exportByMetricNameWithOneMetricMultipleTags() {
        JsonMetadataExporter exporter = new JsonMetadataExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        applicationRegistry.counter("counter1",
                new Tag("key1", "value1"),
                new Tag("color", "blue"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonArray outerTagsArray = json.getJsonObject("counter1").getJsonArray("tags");
        assertEquals(1, outerTagsArray.size());
        JsonArray innerArray = outerTagsArray.getJsonArray(0);
        assertEquals(2, innerArray.size());
        assertEquals("color=blue", innerArray.getString(0));
        assertEquals("key1=value1", innerArray.getString(1));
    }

    @Test
    public void exportByMetricNameWithMultipleMetrics() {
        JsonMetadataExporter exporter = new JsonMetadataExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        applicationRegistry.counter("counter1", new Tag("key1", "value1"));
        applicationRegistry.counter("counter1", new Tag("key1", "value2"));
        applicationRegistry.counter("counter1", new Tag("key1", "value3"));

        String result = exporter.exportMetricsByName(MetricRegistry.Type.APPLICATION, "counter1").toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        JsonArray outerTagsArray = json.getJsonObject("counter1").getJsonArray("tags");
        assertEquals(3, outerTagsArray.size());

        JsonArray innerArray1 = outerTagsArray.getJsonArray(0);
        assertEquals(1, innerArray1.size());
        assertEquals("key1=value1", innerArray1.getString(0));

        JsonArray innerArray2 = outerTagsArray.getJsonArray(1);
        assertEquals(1, innerArray2.size());
        assertEquals("key1=value2", innerArray2.getString(0));

        JsonArray innerArray3 = outerTagsArray.getJsonArray(2);
        assertEquals(1, innerArray3.size());
        assertEquals("key1=value3", innerArray3.getString(0));
    }

    @Test
    public void exportByScopeWithMultipleMetrics() {
        JsonMetadataExporter exporter = new JsonMetadataExporter();
        MetricRegistry applicationRegistry = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
        applicationRegistry.counter("counter1", new Tag("key1", "value1"));
        applicationRegistry.counter("counter1", new Tag("key1", "value2"));
        applicationRegistry.counter("counter2", new Tag("color", "red"));

        String result = exporter.exportOneScope(MetricRegistry.Type.APPLICATION).toString();
        JsonObject json = Json.createReader(new StringReader(result)).read().asJsonObject();

        // check items for counter1
        JsonArray outerTagsArray = json.getJsonObject("counter1").getJsonArray("tags");
        assertEquals(2, outerTagsArray.size());

        JsonArray innerArray1 = outerTagsArray.getJsonArray(0);
        assertEquals(1, innerArray1.size());
        assertEquals("key1=value1", innerArray1.getString(0));

        JsonArray innerArray2 = outerTagsArray.getJsonArray(1);
        assertEquals(1, innerArray2.size());
        assertEquals("key1=value2", innerArray2.getString(0));

        // check items for counter2
        outerTagsArray = json.getJsonObject("counter2").getJsonArray("tags");
        assertEquals(1, outerTagsArray.size());

        innerArray1 = outerTagsArray.getJsonArray(0);
        assertEquals(1, innerArray1.size());
        assertEquals("color=red", innerArray1.getString(0));
    }

}
