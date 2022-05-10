package io.smallrye.metrics.legacyapi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;

import io.smallrye.metrics.SmallRyeMetricsMessages;
import io.smallrye.metrics.elementdesc.MemberInfo;

/**
 * During CDI registration in MetricsMetadata this is used to store
 * metrics (MetricID) related to the Member in which the member/element
 * was annotated with one of the metric annotations. The interceptors will
 * later use this member mappings to find the MetricIDs associated to it,
 * query the Metric Registry's registry and retrieve the metric
 */
public class MemberToMetricMappings {

    public MemberToMetricMappings() {
        counters = new HashMap<>();
        timers = new HashMap<>();
    }

    private final Map<MemberInfo, Set<MetricID>> counters;
    private final Map<MemberInfo, Set<MetricID>> timers;

    public Set<MetricID> getCounters(MemberInfo member) {
        return counters.get(member);
    }

    public Set<MetricID> getTimers(MemberInfo member) {
        return timers.get(member);
    }

    public void addMetric(MemberInfo member, MetricID metricID, MetricType metricType) {
        switch (metricType) {
            case COUNTER:
                counters.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            case TIMER:
                timers.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
                break;
            default:
                throw SmallRyeMetricsMessages.msg.unknownMetricType();
        }
        //SmallRyeMetricsLogging.log.matchingMemberToMetric(member, metricID, metricType);
    }

}
