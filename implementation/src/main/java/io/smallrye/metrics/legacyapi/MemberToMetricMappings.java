package io.smallrye.metrics.legacyapi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.metrics.MetricID;

import io.smallrye.metrics.elementdesc.MemberInfo;

/**
 * During CDI registration in MetricsMetadata this is used to store
 * metrics (MetricID) related to the Member in which the member/element
 * was annotated with one of the metric annotations. The interceptors will
 * later use this member mappings to find the MetricIDs associated to it,
 * query the Metric Registry's registry and retrieve the metric
 */
public class MemberToMetricMappings {

    private static final String CLASS_NAME = MemberToMetricMappings.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

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

    public void addTimer(MemberInfo member, MetricID metricID) {
        timers.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
        LOGGER.logp(Level.FINER, CLASS_NAME, "addTimer", "Matching member {0} to metric ID={1} and type={2}",
                new Object[] { member, metricID, "Timer" });
    }

    public void addCounter(MemberInfo member, MetricID metricID) {
        counters.computeIfAbsent(member, id -> new HashSet<>()).add(metricID);
        LOGGER.logp(Level.FINER, CLASS_NAME, "addCounter", "Matching member {0} to metric ID={1} and type={2}",
                new Object[] { member, metricID, "Counter" });
    }
}
