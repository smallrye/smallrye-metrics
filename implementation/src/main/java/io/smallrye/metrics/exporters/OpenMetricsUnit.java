/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package io.smallrye.metrics.exporters;

import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_DAY;
import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_HOUR;
import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_MICROSECOND;
import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_MILLI;
import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_MINUTE;
import static io.smallrye.metrics.exporters.ExporterUtil.NANOS_PER_SECOND;

import java.util.Optional;

import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * @author hrupp
 */
public class OpenMetricsUnit {

    private OpenMetricsUnit() {
    }

    /**
     * Determines the basic unit to be used by OpenMetrics exporter based on the input unit from parameter.
     * That is:
     * - for memory size units, returns "bytes"
     * - for time units, returns "seconds"
     * - for any other unit, returns the input unit itself
     */
    public static String getBaseUnitAsOpenMetricsString(Optional<String> optUnit) {

        if (!optUnit.isPresent()) {
            return MetricUnits.NONE;
        }

        String unit = optUnit.get();

        String out;
        switch (unit) {

            /* Represents bits. Not defined by SI, but by IEC 60027 */
            case MetricUnits.BITS:
                /* 1000 {@link #BITS} */
            case MetricUnits.KILOBITS:
                /* 1000 {@link #KIBIBITS} */
            case MetricUnits.MEGABITS:
                /* 1000 {@link #MEGABITS} */
            case MetricUnits.GIGABITS:
                /* 1024 {@link #BITS} */
            case MetricUnits.KIBIBITS:
                /* 1024 {@link #KIBIBITS} */
            case MetricUnits.MEBIBITS:
                /* 1024 {@link #MEBIBITS} */
            case MetricUnits.GIBIBITS:
                /* 8 {@link #BITS} */
            case MetricUnits.BYTES:
                /* 1000 {@link #BYTES} */
            case MetricUnits.KILOBYTES:
                /* 1000 {@link #KILOBYTES} */
            case MetricUnits.MEGABYTES:
                /* 1000 {@link #MEGABYTES} */
            case MetricUnits.GIGABYTES:
                out = "bytes";
                break;

            /* 1/1000 {@link #MICROSECONDS} */
            case MetricUnits.NANOSECONDS:
                /* 1/1000 {@link #MILLISECONDS} */
            case MetricUnits.MICROSECONDS:
                /* 1/1000 {@link #SECONDS} */
            case MetricUnits.MILLISECONDS:
                /* Represents seconds */
            case MetricUnits.SECONDS:
                /* 60 {@link #SECONDS} */
            case MetricUnits.MINUTES:
                /* 60 {@link #MINUTES} */
            case MetricUnits.HOURS:
                /* 24 {@link #HOURS} */
            case MetricUnits.DAYS:
                out = "seconds";
                break;
            default:
                out = unit;
        }
        return out;
    }

    /**
     * Scales the value (time or memory size) interpreted using inputUnit to the base unit for OpenMetrics exporter
     * That means:
     * - values for memory size units are scaled to bytes
     * - values for time units are scaled to seconds
     * - values for other units are returned unchanged
     */
    public static Double scaleToBase(String inputUnit, Double value) {

        Double out;

        switch (inputUnit) {

            case MetricUnits.BITS:
                out = value / 8;
                break;
            case MetricUnits.KILOBITS:
                out = value * 1_000 / 8;
                break;
            case MetricUnits.MEGABITS:
                out = value * 1_000_000 / 8;
                break;
            case MetricUnits.GIGABITS:
                out = value * 1_000_000_000 / 8;
                break;
            /* 1024 {@link #BITS} */
            case MetricUnits.KIBIBITS:
                out = value * 128;
                break;
            case MetricUnits.MEBIBITS:
                out = value * 1_024 * 128;
                break;
            case MetricUnits.GIBIBITS:
                out = value * 1_024 * 1_024 * 128;
                break;
            case MetricUnits.BYTES:
                out = value;
                break;
            case MetricUnits.KILOBYTES:
                out = value * 1_000;
                break;
            case MetricUnits.MEGABYTES:
                out = value * 1_000_000;
                break;
            case MetricUnits.GIGABYTES:
                out = value * 1_000_000_000;
                break;
            case MetricUnits.NANOSECONDS:
                out = ExporterUtil.convertNanosTo(value, MetricUnits.SECONDS);
                break;
            case MetricUnits.MICROSECONDS:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_MICROSECOND, MetricUnits.SECONDS);
                break;
            case MetricUnits.MILLISECONDS:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_MILLI, MetricUnits.SECONDS);
                break;
            case MetricUnits.SECONDS:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_SECOND, MetricUnits.SECONDS);
                break;
            case MetricUnits.MINUTES:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_MINUTE, MetricUnits.SECONDS);
                break;
            case MetricUnits.HOURS:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_HOUR, MetricUnits.SECONDS);
                break;
            case MetricUnits.DAYS:
                out = ExporterUtil.convertNanosTo(value * NANOS_PER_DAY, MetricUnits.SECONDS);
                break;
            default:
                out = value;
        }
        return out;
    }
}
