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
package io.smallrye.metrics;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * @author hrupp
 *
 */
public class ExtendedMetadata extends DefaultMetadata {

    private final String mbean;
    private final boolean multi;
    /**
     * Optional configuration to prepend the microprofile scope to the metric name
     * when it is exported to the OpenMetrics format.
     *
     * By default, the option is empty() and will not be taken into account.
     * If true, the scope is prepended to the metric name when the OpenMetrics name is generated (e.g. {@code vendor_foo}.
     * If false, the scope is added to the metric tags instead (e.g. foo{microprofile_scope="vendor"}.
     *
     * This option has precedence over the global configuration
     * {@link io.smallrye.metrics.exporters.OpenMetricsExporter#SMALLRYE_METRICS_USE_PREFIX_FOR_SCOPE}.
     */
    private final Optional<Boolean> prependsScopeToOpenMetricsName;

    /**
     * If true, the scope in OpenMetrics export will be skipped completely.
     */
    private final boolean skipsScopeInOpenMetricsExportCompletely;

    /**
     * Overrides the name under which the metric is presented in OpenMetrics export.
     * Applies only to non-compound metrics (that means counters and gauges).
     */
    private final Optional<String> openMetricsKeyOverride;

    public ExtendedMetadata(String name, MetricType type) {
        this(name, null, null, type, null, null, false);
    }

    public ExtendedMetadata(String name, MetricType type, String unit, String description,
            boolean skipsScopeInOpenMetricsExportCompletely) {
        this(name, null, description, type, unit, null, false, Optional.of(true), skipsScopeInOpenMetricsExportCompletely,
                null);
    }

    public ExtendedMetadata(String name, MetricType type, String unit, String description,
            boolean skipsScopeInOpenMetricsExportCompletely, String openMetricsKey) {
        this(name, null, description, type, unit, null, false, Optional.of(true), skipsScopeInOpenMetricsExportCompletely,
                openMetricsKey);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit) {
        this(name, displayName, description, typeRaw, unit, null, false, Optional.empty());
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit, String mbean,
            boolean multi) {
        this(name, displayName, description, typeRaw, unit, mbean, multi, Optional.empty());
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit, String mbean,
            boolean multi, Optional<Boolean> prependsScopeToOpenMetricsName) {
        this(name, displayName, description, typeRaw, unit, mbean, multi, prependsScopeToOpenMetricsName, false, null);
    }

    public ExtendedMetadata(String name, String displayName, String description, MetricType typeRaw, String unit, String mbean,
            boolean multi, Optional<Boolean> prependsScopeToOpenMetricsName, boolean skipsScopeInOpenMetricsExportCompletely,
            String openMetricsKeyOverride) {
        super(name, displayName, description, typeRaw, unit);
        this.mbean = mbean;
        this.multi = multi;
        this.prependsScopeToOpenMetricsName = prependsScopeToOpenMetricsName;
        this.skipsScopeInOpenMetricsExportCompletely = skipsScopeInOpenMetricsExportCompletely;
        this.openMetricsKeyOverride = Optional.ofNullable(openMetricsKeyOverride);
    }

    public String getMbean() {
        return mbean;
    }

    public boolean isMulti() {
        return multi;
    }

    public Optional<Boolean> prependsScopeToOpenMetricsName() {
        return prependsScopeToOpenMetricsName;
    }

    public boolean isSkipsScopeInOpenMetricsExportCompletely() {
        return skipsScopeInOpenMetricsExportCompletely;
    }

    public Optional<String> getOpenMetricsKeyOverride() {
        return openMetricsKeyOverride;
    }

    public static ExtendedMetadataBuilder builder() {
        return new ExtendedMetadataBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ExtendedMetadata that = (ExtendedMetadata) o;
        return multi == that.multi &&
                Objects.equals(prependsScopeToOpenMetricsName, that.prependsScopeToOpenMetricsName) &&
                Objects.equals(skipsScopeInOpenMetricsExportCompletely, that.skipsScopeInOpenMetricsExportCompletely) &&
                Objects.equals(openMetricsKeyOverride, that.openMetricsKeyOverride) &&
                Objects.equals(mbean, that.mbean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mbean, multi, prependsScopeToOpenMetricsName,
                skipsScopeInOpenMetricsExportCompletely, openMetricsKeyOverride);
    }
}
