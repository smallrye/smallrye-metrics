/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics.tck.rest;

import java.io.File;

import javax.enterprise.inject.spi.Extension;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.weld.environment.deployment.discovery.BeanArchiveHandler;

import io.smallrye.config.SmallRyeConfigProviderResolver;
import io.smallrye.config.inject.ConfigExtension;
import io.smallrye.metrics.testsuite.MetricsInitializer;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 *         <br>
 *         Date: 6/19/18
 */
public class ArchiveProcessor implements ProtocolArchiveProcessor {
    @Override
    public void process(TestDeployment testDeployment, Archive<?> protocolArchive) {
        WebArchive war = (WebArchive) protocolArchive;
        war.addAsWebInfResource("WEB-INF/jboss-web.xml", "jboss-web.xml");
        String[] deps = {
                "io.smallrye:smallrye-config-1.3",
                "io.smallrye:smallrye-metrics-2.1",
                "io.smallrye:smallrye-metrics-testsuite-common",
                "org.jboss.weld.servlet:weld-servlet-core"
        };

        File[] dependencies = Maven.resolver().loadPomFromFile(new File("pom.xml")).resolve(deps).withTransitivity().asFile();

        war.addAsLibraries(dependencies);

        war.addClass(SmallRyeBeanArchiveHandler.class);
        war.addClass(MetricsHttpServlet.class);
        war.addClass(MetricsInitializer.class);
        war.addAsResource("io/smallrye/metrics/base-metrics.properties", "/io/smallrye/metrics/base-metrics.properties");
        war.addAsServiceProvider(BeanArchiveHandler.class, SmallRyeBeanArchiveHandler.class);
        war.addAsServiceProvider(Extension.class, ConfigExtension.class);
        war.addAsServiceProvider(ConfigProviderResolver.class, SmallRyeConfigProviderResolver.class);
    }
}
