/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
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
package io.smallrye.metrics.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.Before;
import org.junit.Test;

import io.smallrye.metrics.ExtendedMetadataAndTags;
import io.smallrye.metrics.JmxWorker;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 *         <br>
 *         Date: 6/29/18
 */
public class JmxRegistrarTest {

    private JmxRegistrar registrar = new JmxRegistrar();
    private List<ExtendedMetadataAndTags> metadataList;

    @Before
    public void setUp() throws IOException {
        InputStream stream = JmxRegistrarTest.class.getResourceAsStream("/base-metrics.properties");
        metadataList = registrar.loadMetadataFromProperties(stream);
    }

    @Test
    public void shouldLoadClassLoaderMetadata() {
        assertThat(getMetadataCalled("classloader.*")).hasSize(3);

        ExtendedMetadataAndTags loadedClasses = getSingleMatch("classloader\\.currentLoadedClass\\.count");

        assertThat(loadedClasses.getMetadata().getName()).isEqualTo("classloader.currentLoadedClass.count");
        assertThat(loadedClasses.getMetadata().getDescription())
                .isEqualTo("Displays the number of classes that are currently " +
                        "loaded in the Java virtual machine.");
        assertThat(loadedClasses.getMetadata().getDisplayName()).isEqualTo("Current Loaded Class Count");
        assertThat(loadedClasses.getMetadata().getMbean()).isEqualTo("java.lang:type=ClassLoading/LoadedClassCount");
        assertThat(loadedClasses.getMetadata().getType()).isEqualTo("counter");
        assertThat(loadedClasses.getMetadata().getUnit()).isEqualTo("none");
    }

    @Test
    public void shouldLoadGcMetadata() {
        assertThat(getMetadataCalled("gc.*")).hasSize(2);

        ExtendedMetadataAndTags gcCount = getSingleMatch("gc.count");

        assertThat(gcCount.getMetadata().getDisplayName()).isEqualTo("Garbage Collection Count");
        assertThat(gcCount.getMetadata().isMulti()).isTrue();
        assertThat(gcCount.getMetadata().getName()).isEqualTo("gc.count");

        ExtendedMetadataAndTags gcTime = getSingleMatch("gc.time");
        assertThat(gcTime.getMetadata().getDisplayName()).isEqualTo("Garbage Collection Time");
        assertThat(gcTime.getMetadata().isMulti()).isTrue();

    }

    @Test
    public void shouldReplaceMultipleWildcards() {
        assertThat(getMetadataCalled("test_key")).hasSize(1);

        final ExtendedMetadataAndTags extendedMetadata = getSingleMatch("test_key");
        assertThat(extendedMetadata.getMetadata().getName()).isEqualTo("test_key");
        assertThat(extendedMetadata.getMetadata().getDescription()).isEqualTo("Description %s1-%s2");
        assertThat(extendedMetadata.getMetadata().getDisplayName()).isEqualTo("Display Name %s1-%s2");
        assertThat(extendedMetadata.getMetadata().getMbean()).isEqualTo("java.nio:name=%s2,type=%s1/ObjectName");
        assertThat(extendedMetadata.getTags()).contains(
                new Tag("type", "%s1"),
                new Tag("name", "%s2"));
        assertThat(extendedMetadata.getMetadata().getType()).isEqualTo("counter");
        assertThat(extendedMetadata.getMetadata().getUnit()).isEqualTo("none");

        final List<ExtendedMetadataAndTags> metadataList = Lists.list(extendedMetadata);

        JmxWorker.instance().expandMultiValueEntries(metadataList);

        final ExtendedMetadataAndTags extendedMetadata1 = metadataList.get(0);
        assertThat(extendedMetadata1.getMetadata().getDescription()).isEqualTo("Description BufferPool-mapped");
        assertThat(extendedMetadata1.getMetadata().getDisplayName()).isEqualTo("Display Name BufferPool-mapped");
        assertThat(extendedMetadata1.getMetadata().getMbean()).isEqualTo("java.nio:name=mapped,type=BufferPool/ObjectName");
        assertThat(extendedMetadata1.getTags()).contains(
                new Tag("type", "BufferPool"),
                new Tag("name", "mapped"));

        final ExtendedMetadataAndTags extendedMetadata2 = metadataList.get(1);
        assertThat(extendedMetadata2.getMetadata().getDescription()).isEqualTo("Description BufferPool-direct");
        assertThat(extendedMetadata2.getMetadata().getDisplayName()).isEqualTo("Display Name BufferPool-direct");
        assertThat(extendedMetadata2.getMetadata().getMbean()).isEqualTo("java.nio:name=direct,type=BufferPool/ObjectName");
        assertThat(extendedMetadata2.getTags()).contains(
                new Tag("type", "BufferPool"),
                new Tag("name", "direct"));
    }

    private ExtendedMetadataAndTags getSingleMatch(String namePattern) {
        List<ExtendedMetadataAndTags> gcList = getMetadataCalled(namePattern);
        assertThat(gcList).hasSize(1);
        return gcList.iterator().next();
    }

    private List<ExtendedMetadataAndTags> getMetadataCalled(String namePattern) {
        return metadataList.stream()
                .filter(meta -> meta.getMetadata().getName().matches(namePattern))
                .collect(Collectors.toList());
    }

}
