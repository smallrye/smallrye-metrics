package io.smallrye.metrics.setup;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Optional;
import java.util.Properties;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import io.smallrye.metrics.SmallRyeMetricsLogging;

public class SmallRyeMetricsCdiExtension implements Extension {

    void logVersion(@Observes BeforeBeanDiscovery bbd) {
        SmallRyeMetricsLogging.log.logSmallRyeMetricsVersion(getImplementationVersion().orElse("unknown"));
    }

    private Optional<String> getImplementationVersion() {
        return AccessController.doPrivileged(new PrivilegedAction<Optional<String>>() {
            @Override
            public Optional<String> run() {
                Properties properties = new Properties();
                try {
                    final InputStream resource = this.getClass().getClassLoader().getResourceAsStream("project.properties");
                    if (resource != null) {
                        properties.load(resource);
                        return Optional.ofNullable(properties.getProperty("smallrye.metrics.version"));
                    }
                } catch (IOException e) {
                    SmallRyeMetricsLogging.log.unableToDetectVersion();
                }
                return Optional.empty();
            }
        });
    }
}
