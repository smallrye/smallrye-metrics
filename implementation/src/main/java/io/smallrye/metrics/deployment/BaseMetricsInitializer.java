/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.smallrye.metrics.deployment;

import io.smallrye.metrics.runtime.ConfigReader;
import io.smallrye.metrics.runtime.ExtendedMetadata;
import io.smallrye.metrics.runtime.JmxWorker;
import io.smallrye.metrics.runtime.MetadataList;
import io.smallrye.metrics.runtime.Tag;
import io.smallrye.metrics.runtime.mbean.MCounterImpl;
import io.smallrye.metrics.runtime.mbean.MGaugeImpl;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Heiko W. Rupp
 */
@ApplicationScoped
public class BaseMetricsInitializer {

    public static final String MAPPING_FILE_PATH = "/io/smallrye/metrics/mapping.yml";

    public void initialize(@Observes @Initialized(ApplicationScoped.class) Object ignored) {
        initBaseAndVendorConfiguration();
    }

    /**
     * Read a list of mappings that contains the base and vendor metrics
     * along with their metadata.
     */
    private void initBaseAndVendorConfiguration() {

        InputStream is = getClass().getResourceAsStream(MAPPING_FILE_PATH);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MAPPING_FILE_PATH);
        }

        if (is != null) {
            ConfigReader cr = new ConfigReader();
            MetadataList ml = cr.readConfig(is);

            Config config = ConfigProvider.getConfig();

            Optional<String> globalTagsFromConfig = config.getOptionalValue("mp.metrics.tags", String.class);
            List<Tag> globalTags = convertToTags(globalTagsFromConfig);

            // Turn the multi-entry query expressions into concrete entries.
            worker.expandMultiValueEntries(ml.getBase());
            worker.expandMultiValueEntries(ml.getVendor());

            for (ExtendedMetadata em : ml.getBase()) {
                em.processTags(globalTags);
                Metric type = getType(em);
                registry.register(em, type);
            }
            for (ExtendedMetadata em : ml.getVendor()) {
                em.processTags(globalTags);
                Metric type = getType(em);
                registry.register(em, type);
            }
        } else {
            throw new IllegalStateException("Was not able to find the mapping file 'mapping.yml'");
        }
    }

    private Metric getType(ExtendedMetadata em) {
        Metric out;
        switch (em.getTypeRaw()) {
            case GAUGE:
                out = new MGaugeImpl(worker, em.getMbean());
                break;
            case COUNTER:
                out = new MCounterImpl(worker, em.getMbean());
                break;
            default:
                throw new IllegalStateException("Not yet supported: " + em);
        }
        return out;
    }

    private List<Tag> convertToTags(Optional<String> globalTags) {
        List<Tag> tags = new ArrayList<>();

        if (!globalTags.isPresent()) {
            return tags;
        }
        String globalTagsString = globalTags.get();
        if (!globalTagsString.equals("")) {
            String[] singleTags = globalTagsString.split(",");
            for (String singleTag : singleTags) {
                tags.add(new Tag(singleTag.trim()));
            }
        }
        return tags;
    }

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    MetricRegistry registry;

    @Inject
    JmxWorker worker;

}
