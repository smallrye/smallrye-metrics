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

package io.smallrye.metrics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;

import io.smallrye.metrics.elementdesc.MemberInfo;

public class MemberToMetricMappings {

    MemberToMetricMappings() {
        counters = new HashMap<>();
        concurrentGauges = new HashMap<>();
        meters = new HashMap<>();
        timers = new HashMap<>();
        simpleTimers = new HashMap<>();
    }

    private Map<MemberInfo, Set<MetricID>> counters;
    private Map<MemberInfo, Set<MetricID>> concurrentGauges;
    private Map<MemberInfo, Set<MetricID>> meters;
    private Map<MemberInfo, Set<MetricID>> timers;
    private Map<MemberInfo, Set<MetricID>> simpleTimers;

    public Set<MetricID> getCounters(MemberInfo member) {
        return counters.get(member);
    }

    public Set<MetricID> getConcurrentGauges(MemberInfo member) {
        return concurrentGauges.get(member);
    }

    public Set<MetricID> getMeters(MemberInfo member) {
        return meters.get(member);
    }

    public Set<MetricID> getTimers(MemberInfo member) {
        return timers.get(member);
    }

    public Set<MetricID> getSimpleTimers(MemberInfo member) {
        return simpleTimers.get(member);
    }

    public void addMetric(MemberInfo member, MetricID metricID, MetricType metricType) {
        switch (metricType) {
            case COUNTER:
                counters.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            case CONCURRENT_GAUGE:
                concurrentGauges.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            case METERED:
                meters.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            case TIMER:
                timers.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            case SIMPLE_TIMER:
                simpleTimers.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            default:
                throw SmallRyeMetricsMessages.msg.unknownMetricType();
        }
        SmallRyeMetricsLogging.log.matchingMemberToMetric(member, metricID, metricType);
    }

}
