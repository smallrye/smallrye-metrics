/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
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

package io.smallrye.metrics.tck.optional;

import java.io.File;

import jakarta.ws.rs.ext.Providers;

import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import io.smallrye.metrics.jaxrs.JaxRsMetricsFilter;
import io.smallrye.metrics.jaxrs.JaxRsMetricsServletFilter;

public class JaxRsMetricsActivatingProcessor implements ProtocolArchiveProcessor {

    @Override
    public void process(TestDeployment testDeployment, Archive<?> archive) {
        WebArchive war = (WebArchive) archive;

        String[] deps = {
                "io.smallrye:smallrye-config",
                "io.smallrye:smallrye-metrics",
                "io.smallrye:smallrye-metrics-testsuite-common",
                "org.eclipse.microprofile.metrics:microprofile-metrics-api",
                "org.eclipse.microprofile.config:microprofile-config-api"
        };
        File[] dependencies = Maven.resolver().loadPomFromFile(new File("pom.xml")).resolve(deps).withTransitivity().asFile();
        war.addAsLibraries(dependencies);

        war.addClass(MetricsHttpServlet.class);
        war.addClass(JaxRsMetricsFilter.class);
        war.addClass(JaxRsMetricsServletFilter.class);

        // change application context root to '/' because the TCK assumes that the metrics
        // will be available at '/metrics', and in our case the metrics servlet is located
        // within the application itself, we don't use WildFly's built-in support for metrics
        war.addAsWebInfResource("WEB-INF/jboss-web.xml", "jboss-web.xml");

        // activate the servlet filter
        war.setWebXML("WEB-INF/web.xml");

        // activate the JAX-RS request filter
        war.addAsServiceProvider(Providers.class.getName(), JaxRsMetricsFilter.class.getName());

        // exclude built-in Metrics and Config from WildFly
        war.addAsManifestResource("META-INF/jboss-deployment-structure.xml", "jboss-deployment-structure.xml");
    }

}
