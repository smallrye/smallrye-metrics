package io.smallrye.metrics.setup;

public interface ApplicationNameResolver {

    static ApplicationNameResolver DEFAULT = () -> null;

    public String getApplicationName();
}
