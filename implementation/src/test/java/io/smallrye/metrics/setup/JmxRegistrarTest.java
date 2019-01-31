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
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.JmxWorker;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 6/29/18
 */
public class JmxRegistrarTest {

    private JmxRegistrar registrar = new JmxRegistrar();
    private List<ExtendedMetadata> metadataList;

    @Before
    public void setUp() throws IOException {
        InputStream stream = JmxRegistrarTest.class.getResourceAsStream("/base-metrics.properties");
        metadataList = registrar.loadMetadataFromProperties(stream);
    }

    @Test
    public void shouldLoadClassLoaderMetadata() {
        assertThat(getMetadataCalled("classloader.*")).hasSize(3);

        ExtendedMetadata loadedClasses = getSingleMatch("classloader\\.currentLoadedClass\\.count");

        assertThat(loadedClasses.getName()).isEqualTo("classloader.currentLoadedClass.count");
        assertThat(loadedClasses.getDescription().get()).isEqualTo("Displays the number of classes that are currently " +
                                                                  "loaded in the Java virtual machine.");
        assertThat(loadedClasses.getDisplayName()).isEqualTo("Current Loaded Class Count");
        assertThat(loadedClasses.getMbean()).isEqualTo("java.lang:type=ClassLoading/LoadedClassCount");
        assertThat(loadedClasses.getTags()).isEmpty();
        assertThat(loadedClasses.getType()).isEqualTo("counter");
        assertThat(loadedClasses.getUnit().get()).isEqualTo("none");
    }

    @Test
    public void shouldLoadGcMetadata() {
        assertThat(getMetadataCalled("gc.*")).hasSize(2);

        ExtendedMetadata gcCount = getSingleMatch("gc\\.%s\\.count");

        assertThat(gcCount.getDisplayName()).isEqualTo("Garbage Collection Count");
        assertThat(gcCount.isMulti()).isTrue();
        assertThat(gcCount.getName()).isEqualTo("gc.%s.count");


        ExtendedMetadata gcTime = getSingleMatch("gc\\.%s\\.time");
        assertThat(gcTime.getDisplayName()).isEqualTo("Garbage Collection Time");
        assertThat(gcTime.isMulti()).isTrue();

    }

    @Test
    public void shouldReplaceMultipleWildcards() {
    	assertThat(getMetadataCalled("test_key")).hasSize(1);
    	
    	final ExtendedMetadata extendedMetadata = getSingleMatch("test_key");
        assertThat(extendedMetadata.getName()).isEqualTo("test_key");
        assertThat(extendedMetadata.getDescription()).isEqualTo("Description %s1-%s2");
        assertThat(extendedMetadata.getDisplayName()).isEqualTo("Display Name %s1-%s2");
        assertThat(extendedMetadata.getMbean()).isEqualTo("java.nio:name=%s2,type=%s1/ObjectName");
        assertThat(extendedMetadata.getTags()).contains(entry("type", "%s1"), entry("name", "%s2"));
        assertThat(extendedMetadata.getType()).isEqualTo("counter");
        assertThat(extendedMetadata.getUnit()).isEqualTo("none");
        
        final List<ExtendedMetadata> metadataList = Lists.list(extendedMetadata);
        
        JmxWorker.instance().expandMultiValueEntries(metadataList);
        
        assertThat(metadataList.size() == 2);

        final ExtendedMetadata extendedMetadata1 = metadataList.get(0);
        assertThat(extendedMetadata1.getDescription()).isEqualTo("Description BufferPool-mapped");
        assertThat(extendedMetadata1.getDisplayName()).isEqualTo("Display Name BufferPool-mapped");
        assertThat(extendedMetadata1.getMbean()).isEqualTo("java.nio:name=mapped,type=BufferPool/ObjectName");
        assertThat(extendedMetadata1.getTags()).contains(entry("type", "BufferPool"), entry("name", "mapped"));
        
        final ExtendedMetadata extendedMetadata2 = metadataList.get(1);
        assertThat(extendedMetadata2.getDescription()).isEqualTo("Description BufferPool-direct");
        assertThat(extendedMetadata2.getDisplayName()).isEqualTo("Display Name BufferPool-direct");
        assertThat(extendedMetadata2.getMbean()).isEqualTo("java.nio:name=direct,type=BufferPool/ObjectName");
        assertThat(extendedMetadata2.getTags()).contains(entry("type", "BufferPool"), entry("name", "direct"));      
    }
    
    private ExtendedMetadata getSingleMatch(String namePattern) {
        List<ExtendedMetadata> gcList = getMetadataCalled(namePattern);
        assertThat(gcList).hasSize(1);
        return gcList.iterator().next();
    }

    private List<ExtendedMetadata> getMetadataCalled(String namePattern) {
        return metadataList.stream()
                .filter(meta -> meta.getName().matches(namePattern))
                .collect(Collectors.toList());
    }

}
