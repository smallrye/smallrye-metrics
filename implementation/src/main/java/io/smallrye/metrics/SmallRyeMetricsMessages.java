package io.smallrye.metrics;

import java.lang.reflect.Member;

import javax.management.MalformedObjectNameException;

import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.MetricID;
import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = "SRMET", length = 5)
public interface SmallRyeMetricsMessages {

    SmallRyeMetricsMessages msg = Messages.getBundle(SmallRyeMetricsMessages.class);

    @Message(id = 0, value = "Unknown metric type")
    IllegalArgumentException unknownMetricType();

    @Message(id = 2, value = "No metric with ID %s found in registry")
    IllegalStateException noMetricFoundInRegistry(MetricID metricID);

    @Message(id = 3, value = "No metric mapped for %s")
    IllegalStateException noMetricMappedForMember(Member member);

    @Message(id = 4, value = "Unable to retrieve name for parameter %s")
    UnsupportedOperationException unableToRetrieveParameterName(AnnotatedParameter<?> parameter);

    @Message(id = 5, value = "Unable to retrieve metric name for injection point %s")
    UnsupportedOperationException unableToRetrieveMetricNameForInjectionPoint(InjectionPoint ip);

    @Message(id = 6, value = "No metric is present")
    IllegalStateException noMetricPresent();

    @Message(id = 7, value = "Not a valid key=value pair: %s")
    IllegalArgumentException notAKeyValuePair(String string);

    @Message(id = 8, value = "Method must not be called")
    IllegalStateException mustNotBeCalled();

    @Message(id = 9, value = "Gauge with id %s already exists")
    IllegalArgumentException gaugeWithIdAlreadyExists(MetricID metricID);

    @Message(id = 10, value = "Metric name must not be null or empty")
    IllegalArgumentException metricNameMustNotBeNullOrEmpty();

    @Message(id = 11, value = "Metric %s already exists under a different type (%s)")
    IllegalStateException metricExistsUnderDifferentType(String name, String existingType);

    @Message(id = 12, value = "Unit is different from the unit in previous usage (%s)")
    IllegalStateException unitDiffersFromPreviousUsage(String existingUnit);

    @Message(id = 13, value = "Description is different from the description in previous usage")
    IllegalStateException descriptionDiffersFromPreviousUsage();

    @Message(id = 14, value = "Display name is different from the display name in previous usage")
    IllegalStateException displayNameDiffersFromPreviousUsage();

    @Message(id = 15, value = "A metric with name %s already exists")
    IllegalStateException metricWithNameAlreadyExists(String name);

    @Message(id = 16, value = "Unable to infer a metric type")
    IllegalStateException unableToInferMetricType();

    @Message(id = 19, value = "Unknown metric annotation type %s")
    IllegalArgumentException unknownMetricAnnotationType(Class<?> annotationType);

    @Message(id = 20, value = "Malformed object name")
    IllegalStateException malformedObjectName(@Cause MalformedObjectNameException cause);

}
