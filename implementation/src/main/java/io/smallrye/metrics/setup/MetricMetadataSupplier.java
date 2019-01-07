/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
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
 *
 */
package io.smallrye.metrics.setup;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.eclipse.microprofile.metrics.Metadata;

import io.smallrye.metrics.ExtendedMetadata;
import io.smallrye.metrics.OriginTrackedMetadata;

/**
 * Simple class to supply rye with metrics with access control
 *
 */
public final class MetricMetadataSupplier<T extends Metadata> {

    public static final MetricMetadataSupplier<OriginTrackedMetadata> S_OriginTrackedMetadata = new MetricMetadataSupplier<OriginTrackedMetadata>();
    public static final MetricMetadataSupplier<ExtendedMetadata> S_ExtendedMetadata = new MetricMetadataSupplier<ExtendedMetadata>();
    public static final MetricMetadataSupplier<Metadata> S_Metadata = new MetricMetadataSupplier<Metadata>();
    public T initiateMetadata(Supplier<T> supplier){
        if(System.getSecurityManager() == null) {
            return supplier.get();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<T>() {

                @Override
                public T run() {
                    return supplier.get();
                }
            });
        }
    }
}
