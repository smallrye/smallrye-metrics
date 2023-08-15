package io.smallrye.metrics.setup.config;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.logging.Logger;

public class DefaulBucketConfiguration extends PropertyBooleanConfiguration {

    private static final String CLASS_NAME = DefaulBucketConfiguration.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    public DefaulBucketConfiguration(String metricName, boolean value) {
        this.metricName = metricName;
        this.isEnabled = value;
    }

    public static Collection<DefaulBucketConfiguration> parse(String input) {

        ArrayDeque<DefaulBucketConfiguration> metricBucketConfiCollection = new ArrayDeque<DefaulBucketConfiguration>();

        if (input == null || input.length() == 0) {
            return null;
        }

        // not expecting backslashes?
        String[] metricValuePairs = input.split(";");

        // Individual metric name grouping and values
        for (String kvString : metricValuePairs) {

            String[] keyValueSplit = kvString.split("=");

            String metricName = keyValueSplit[0];

            DefaulBucketConfiguration metricDefaultedBucketConfiguration = null;

            //metricGroup=<blank> => default to false
            if (keyValueSplit.length == 1) {
                continue;
            } else {
                boolean isEnabledParam = Boolean.parseBoolean(keyValueSplit[1].trim());

                metricDefaultedBucketConfiguration = new DefaulBucketConfiguration(metricName, isEnabledParam);
            }

            // LIFO - right most configuration takes precedence
            metricBucketConfiCollection.addFirst(metricDefaultedBucketConfiguration);
        }
        return metricBucketConfiCollection;

    }

}
