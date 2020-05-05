package io.smallrye.metrics;

import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import io.smallrye.metrics.elementdesc.MemberInfo;

@MessageLogger(projectCode = "SRMET", length = 5)
public interface SmallRyeMetricsLogging {

    SmallRyeMetricsLogging log = Logger.getMessageLogger(SmallRyeMetricsLogging.class,
            SmallRyeMetricsLogging.class.getPackage().getName());

    /* 1000-1099: welcome and boot logs */

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1000, value = "Unable to detect version of SmallRye Metrics")
    void unableToDetectVersion();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1001, value = "MicroProfile: Metrics activated (SmallRye Metrics version: %s)")
    void logSmallRyeMetricsVersion(String version);

    /* 1100-1199: logs related to application scanning and initialization */

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1100, value = "Metric producer field discovered: %s")
    void producerFieldDiscovered(AnnotatedField<?> field);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1101, value = "Metric producer method discovered: %s")
    void producerMethodDiscovered(AnnotatedMethod<?> method);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(id = 1102, value = "Matching member %s to metric ID=%s and type=%s")
    void matchingMemberToMetric(MemberInfo member, MetricID metricID, MetricType metricType);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1103, value = "Name [%s] did not contain any placeholders or tags, no replacement will be done, check"
            + " the configuration")
    void nameDoesNotContainPlaceHoldersOrTags(String name);

    /* 1200-1299: metric registry logs */

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1200, value = "Register metric [metricId: %s, type: %s]")
    void registerMetric(MetricID metricID, MetricType metricType);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1201, value = "Register metric [metricId: %s, type: %s, origin: %s]")
    void registerMetric(MetricID metricID, MetricType metricType, Object origin);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1202, value = "Removing metrics with [name: %s]")
    void removeMetricsByName(String name);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1203, value = "Removing metric with [id: %s]")
    void removeMetricsById(MetricID id);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1204, value = "Remove metadata for [name: %s]")
    void removeMetadata(String name);

    /* 1300-1399: exporter logs */

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 1300, value = "Unable to export metric %s")
    void unableToExport(String name, @Cause Exception e);

}
