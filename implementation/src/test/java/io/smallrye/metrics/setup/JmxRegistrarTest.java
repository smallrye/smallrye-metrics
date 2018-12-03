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

import io.smallrye.metrics.ExtendedMetadata;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


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
