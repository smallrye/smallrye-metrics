/*
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics.interceptors;

import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.InjectionPoint;

public interface MetricName {

    String of(InjectionPoint point);

    String of(AnnotatedMember<?> member);

    // TODO: expose an SPI so that external strategies can be provided. For example, Camel CDI could provide a property placeholder resolution strategy.
    String of(String attribute);
}
