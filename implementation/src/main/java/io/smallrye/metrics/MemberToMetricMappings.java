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

/**
 * This class represents mappings between Java methods and the set of metric IDs
 * associated with them. This is computed once at boot/build time to avoid having to
 * do runtime reflection on every invocation of the relevant methods.
 *
 * This class does NOT use thread-safe map implementations, so populating the mappings
 * must only be performed by one thread. Querying the mappings later at runtime can be done
 * concurrently.
 */
public class MemberToMetricMappings {

    MemberToMetricMappings() {
        counters = new HashMap<>();
        concurrentGauges = new HashMap<>();
        meters = new HashMap<>();
        timers = new HashMap<>();
        simpleTimers = new HashMap<>();
    }

    private final Map<MemberInfo, Set<MetricID>> counters;
    private final Map<MemberInfo, Set<MetricID>> concurrentGauges;
    private final Map<MemberInfo, Set<MetricID>> meters;
    private final Map<MemberInfo, Set<MetricID>> timers;
    private final Map<MemberInfo, Set<MetricID>> simpleTimers;

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

    public void removeMappingsFor(MemberInfo member, MetricID metricID) {
        removeMapping(counters, member, metricID);
        removeMapping(concurrentGauges, member, metricID);
        removeMapping(meters, member, metricID);
        removeMapping(timers, member, metricID);
        removeMapping(simpleTimers, member, metricID);
    }

    private void removeMapping(Map<MemberInfo, Set<MetricID>> map, MemberInfo member, MetricID metricID) {
        if (map.containsKey(member)) {
            map.get(member).remove(metricID);
        }
    }
}
