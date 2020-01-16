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

package io.smallrye.metrics.jaxrs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Filter for measuring JAX-RS metrics, together with {@link JaxRsMetricsServletFilter}.
 * The reason for having two filters (one based on Servlet API, one on JAX-RS API)
 * is to overcome the limitation of JAX-RS specification in that it does not require
 * implementations to call a ContainerResponseFilter to finish exchanges that led
 * to an unmapped exception. That's why we can't use a ContainerResponseFilter to
 * 'finish' the tracking of a REST call and update the corresponding metrics.
 * For that reason, we have {@link JaxRsMetricsServletFilter}, which is a Servlet Filter,
 * to hook into the responses in this case.
 *
 * To get all of this logic working on a target runtime server, it is necessary to register both the
 * {@link JaxRsMetricsServletFilter} and {@link JaxRsMetricsFilter} to handle incoming requests.
 * {@link JaxRsMetricsServletFilter} must run first, but this will probably be the case always.
 */
public class JaxRsMetricsFilter implements ContainerRequestFilter {

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MetricID metricID = getMetricID(resourceInfo.getResourceClass(),
                resourceInfo.getResourceMethod());
        // store the MetricID so that the servlet filter can update the metric
        requestContext.setProperty("smallrye.metrics.jaxrs.metricID", metricID);
    }

    private MetricID getMetricID(Class<?> resourceClass, Method resourceMethod) {
        Tag classTag = new Tag("class", resourceClass.getName());
        String methodName = resourceMethod.getName();
        String encodedParameterNames = Arrays.stream(resourceMethod.getParameterTypes())
                .map(clazz -> {
                    if (clazz.isArray()) {
                        return clazz.getComponentType().getName() + "[]";
                    } else {
                        return clazz.getName();
                    }
                })
                .collect(Collectors.joining("_"));
        String methodTagValue = encodedParameterNames.isEmpty() ? methodName : methodName + "_" + encodedParameterNames;
        Tag methodTag = new Tag("method", methodTagValue);
        return new MetricID("REST.request", classTag, methodTag);
    }

}
