package io.smallrye.metrics;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.metrics.DefaultMetadata;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Created by bob on 2/5/18.
 */
public class OriginTrackedMetadata extends DefaultMetadata {
    public OriginTrackedMetadata(Object origin, String name, MetricType type, String unit, String description, String displayName, boolean reusable, Map<String,String> tagMap) {

        super(name, displayName, description, type,unit, reusable, tagMap);
        this.origin = origin;
    }

    public Object getOrigin() {
        return this.origin;
    }

    private final Object origin;
}
