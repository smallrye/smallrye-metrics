package io.smallrye.metrics;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.PrometheusConfig;
import io.smallrye.metrics.base.LegacyBaseMetrics;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.setup.ApplicationNameResolver;
import io.smallrye.metrics.setup.MPPrometheusMeterRegistry;

/**
 * @author hrupp
 */
@ApplicationScoped
public class MetricRegistries {

    protected static final String GLOBAL_TAG_MALFORMED_EXCEPTION = "Malformed list of Global Tags. Tag names "
            + "must match the following regex [a-zA-Z_][a-zA-Z0-9_]*."
            + " Global Tag values must not be empty."
            + " Global Tag values MUST escape equal signs `=` and commas `,`"
            + " with a backslash `\\` ";

    protected static final String GLOBAL_TAGS_VARIABLE = "mp.metrics.tags";

    /**
     * This static Tag[] represents the server level global tags retrieved from MP Config for mp.metrics.tags. This value will
     * be 'null' when not initialized. If during
     * initialization and no global tag has been resolved this will be to an array of size 0. Using an array of size 0 is to
     * represent that an attempt on start up was made to
     * resolve the value, but none was found. This prevents later instantiations of MetricRegistry to avoid attempting to
     * resolve the MP Config value for the slight performance
     * boon.
     *
     * This server level value will not change at all throughout the life time of the server as it is defined by env vars or sys
     * props.
     */
    protected static Tag[] SERVER_LEVEL_MPCONFIG_GLOBAL_TAGS = null;

    public static final ThreadLocal<Boolean> MP_APP_METER_REG_ACCESS = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> MP_BASE_METER_REG_ACCESS = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> MP_VENDOR_METER_REG_ACCESS = ThreadLocal.withInitial(() -> false);

    private static final Map<MetricRegistry.Type, MetricRegistry> registries = new ConcurrentHashMap<>();

    /**
     * Filter that only allows registration/retrieval of a Meter Registry by the MP shim
     * ThreadLocal will be set to TRUE right before interfacing with the Micrometer meter registry
     *
     * Since all calls on the MP side w.r.t to Micrometer will be against the global composite registry
     * (for propogation of meters to any future configured meter registries) we need to avoid adding
     * to each of the other existing base, vendor or app registries. Therefore there are individual threadlocals
     * and meter filters.
     *
     * See the Counter/Histrogram/Timer Adapters and the base metric binder used below in resolveMeterRegistry()
     */
    static final MeterFilter mpMeterAppRegistryAccessFilter = MeterFilter.accept(id -> {
        return (MetricRegistries.MP_APP_METER_REG_ACCESS.get().booleanValue() == true) ? true : false;
    });

    static final MeterFilter mpMeterBaseRegistryAccessFilter = MeterFilter.accept(id -> {
        return (MetricRegistries.MP_BASE_METER_REG_ACCESS.get().booleanValue() == true) ? true : false;
    });

    static final MeterFilter mpMeterVendorRegistryAccessFilter = MeterFilter.accept(id -> {
        return (MetricRegistries.MP_VENDOR_METER_REG_ACCESS.get().booleanValue() == true) ? true : false;
    });

    @Produces
    @Default
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    @ApplicationScoped
    public MetricRegistry getApplicationRegistry() {
        return getOrCreate(MetricRegistry.Type.APPLICATION);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.BASE)
    @ApplicationScoped
    public MetricRegistry getBaseRegistry() {
        return getOrCreate(MetricRegistry.Type.BASE);
    }

    @Produces
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    @ApplicationScoped
    public MetricRegistry getVendorRegistry() {
        return getOrCreate(MetricRegistry.Type.VENDOR);
    }

    public static MetricRegistry getOrCreate(MetricRegistry.Type type) {
        return getOrCreate(type, null);
    }

    //FIXME: cheap way of passing in the ApplicationNameResolvr from vendor code to the MetricRegistry
    public static MetricRegistry getOrCreate(MetricRegistry.Type type, ApplicationNameResolver appNameResolver) {
        return registries.computeIfAbsent(type,
                t -> new LegacyMetricRegistryAdapter(type, resolveMeterRegistry(type), appNameResolver));
    }

    private static MeterRegistry resolveMeterRegistry(MetricRegistry.Type type) {
        final MeterRegistry meterRegistry;

        if (type == MetricRegistry.Type.BASE) {
            meterRegistry = new MPPrometheusMeterRegistry(PrometheusConfig.DEFAULT, MetricRegistry.Type.BASE);
            meterRegistry.config().commonTags("scope", MetricRegistry.Type.BASE.getName());
            meterRegistry.config().meterFilter(mpMeterBaseRegistryAccessFilter);

        } else if (type == MetricRegistry.Type.APPLICATION) {
            meterRegistry = new MPPrometheusMeterRegistry(PrometheusConfig.DEFAULT, MetricRegistry.Type.APPLICATION);
            meterRegistry.config().commonTags("scope", MetricRegistry.Type.APPLICATION.getName());
            meterRegistry.config().meterFilter(mpMeterAppRegistryAccessFilter);
        } else if (type == MetricRegistry.Type.VENDOR) {
            meterRegistry = new MPPrometheusMeterRegistry(PrometheusConfig.DEFAULT, MetricRegistry.Type.VENDOR);
            meterRegistry.config().commonTags("scope", MetricRegistry.Type.VENDOR.getName());
            meterRegistry.config().meterFilter(mpMeterVendorRegistryAccessFilter);
        } else {
            meterRegistry = Metrics.globalRegistry;
        }

        meterRegistry.config().meterFilter(MeterFilter.deny());
        Tag[] globalTags = resolveMPConfigGlobalTagsByServer();
        if (globalTags.length != 0) {
            meterRegistry.config().commonTags(Arrays.asList(globalTags));
        }
        Metrics.addRegistry(meterRegistry);

        /*
         * To avoid having to repeat the global tags logic once in the BASE IF stmt above
         * and immediately above (to catch the app and vendor),
         * using another If block to bind the base metrics :(
         *
         * FIXME: refactor
         */
        if (type == MetricRegistry.Type.BASE) {
            MetricRegistries.MP_BASE_METER_REG_ACCESS.set(true);
            new LegacyBaseMetrics().bindTo(Metrics.globalRegistry);
            MetricRegistries.MP_BASE_METER_REG_ACCESS.set(false);
        }
        return meterRegistry;
    }

    @PreDestroy
    public void cleanUp() {
        registries.remove(MetricRegistry.Type.APPLICATION);
    }

    /**
     * Drops a particular registry. If a reference to the same registry type
     * is requested later, a new empty registry will be created for that purpose.
     *
     * @param type Type of registry that should be dropped.
     */
    public static void drop(MetricRegistry.Type type) {
        registries.remove(type);
    }

    /**
     * Drops all registries. If a reference to a registry
     * is requested later, a new empty registry will be created for that purpose.
     */
    public static void dropAll() {
        registries.remove(MetricRegistry.Type.APPLICATION);
        registries.remove(MetricRegistry.Type.BASE);
        registries.remove(MetricRegistry.Type.VENDOR);
    }

    private synchronized static Tag[] resolveMPConfigGlobalTagsByServer() {
        if (SERVER_LEVEL_MPCONFIG_GLOBAL_TAGS == null) {

            //Using MP Config to retreive the mp.metrics.tags Config value
            Optional<String> globalTags = ConfigProvider.getConfig().getOptionalValue(GLOBAL_TAGS_VARIABLE, String.class);

            //evaluate if there exists tag values or set tag[0] to be null for no value;
            SERVER_LEVEL_MPCONFIG_GLOBAL_TAGS = (globalTags.isPresent()) ? parseGlobalTags(globalTags.get()) : new Tag[0];
        }
        return (SERVER_LEVEL_MPCONFIG_GLOBAL_TAGS == null) ? null : SERVER_LEVEL_MPCONFIG_GLOBAL_TAGS;

    }

    /**
     * This will return server level global tag
     * i.e defined in env var or sys props
     *
     * Will return null if no MP Config value is set
     * for the mp.metrics.tags on the server level
     *
     * @return Tag[] The server wide global tag; can return null
     */

    private static Tag[] parseGlobalTags(String globalTags) {
        if (globalTags == null || globalTags.length() == 0) {
            return null;
        }
        String[] kvPairs = globalTags.split("(?<!\\\\),");

        Tag[] arrayOfTags = new Tag[kvPairs.length];
        int count = 0;
        for (String kvString : kvPairs) {

            if (kvString.length() == 0) {
                throw new IllegalArgumentException(GLOBAL_TAG_MALFORMED_EXCEPTION);
            }

            String[] keyValueSplit = kvString.split("(?<!\\\\)=");

            if (keyValueSplit.length != 2 || keyValueSplit[0].length() == 0 || keyValueSplit[1].length() == 0) {
                throw new IllegalArgumentException(GLOBAL_TAG_MALFORMED_EXCEPTION);
            }

            String key = keyValueSplit[0];
            String value = keyValueSplit[1];

            if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid Tag name. Tag names must match the following regex "
                        + "[a-zA-Z_][a-zA-Z0-9_]*");
            }
            value = value.replace("\\,", ",");
            value = value.replace("\\=", "=");

            arrayOfTags[count] = Tag.of(key, value);
            count++;
        }

        return arrayOfTags;
    }

}
