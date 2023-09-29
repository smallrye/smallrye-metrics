package io.smallrye.metrics;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.MetricRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.metrics.base.LegacyBaseMetrics;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.micrometer.MicrometerBackends;
import io.smallrye.metrics.micrometer.RequiresClass;
import io.smallrye.metrics.setup.ApplicationNameResolver;

/**
 * SharedMetricRegistries is used to create/retrieve a MicroProfile Metric's MetricRegistry instance
 * of a provided scope.
 *
 * For each "scope" there exists an individual MicroProfile Metric MetricRegistry which is
 * associated to an single "underlying" Micrometer MeterRegistry that is registered to the Micrometer
 * global registry. This is either a Prometheus MeterRegistry or a "simple" MeterRegistry. By
 * default, it is the Prometheus MeterRegistry unless the MP Config "mp.metrics.prometheus.enabled"
 * is set to false. In which case the simple MeterRegistry is used. Alternatively, if the Prometheus
 * MeterRegistry is not detected on the classpath the simple Meter Registry will be used.
 *
 */
public class SharedMetricRegistries {

    private static final String CLASS_NAME = SharedMetricRegistries.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    protected static final String GLOBAL_TAG_MALFORMED_EXCEPTION = "Malformed list of Global Tags. Tag names "
            + "must match the following regex [a-zA-Z_][a-zA-Z0-9_]*." + " Global Tag values must not be empty."
            + " Global Tag values MUST escape equal signs `=` and commas `,`" + " with a backslash `\\` ";

    protected static final String GLOBAL_TAGS_VARIABLE = "mp.metrics.tags";

    private static final String FQ_PROMETHEUS_CONFIG_PATH = "io.micrometer.prometheus.PrometheusConfig";
    private static final String FQ_PROMETHEUS_METRIC_REGISTRY_PATH = "io.micrometer.prometheus.PrometheusMeterRegistry";

    private static final Map<String, MetricRegistry> registries = new ConcurrentHashMap<>();
    private static boolean isBaseMetricsRegistered = false;

    private static MeterRegistry meterRegistry;

    private static ApplicationNameResolver appNameResolver;

    /*
     * Go through class path to identify what registries are available and register them to Micrometer
     * Global Meter Registry
     */
    static {

        Set<Class<?>> setOfMeterRegistryClasses = new HashSet<Class<?>>();

        /*
         * Rely on a ClassNotFound when reading the @RequiredClass' array of required classes to remove
         * potential Micrometer Backend for processing
         */
        for (Class<?> clazz : MicrometerBackends.classes()) {
            try {
                final RequiresClass requiresClass = (RequiresClass) clazz.getAnnotation(RequiresClass.class);
                final Class<?>[] requiredClass = requiresClass.value();
                setOfMeterRegistryClasses.add(clazz);
            } catch (Exception e) {
                // Did not use WARNING as it will flood console on startup
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, CLASS_NAME, null, "Required classes for \"{0}\" not found on classpath",
                            clazz.getName());
                }
            }
        }

        /*
         * For each potential Micrometer Backend, create an instance of it through reflection. Using the
         * abstract class to call the produce() method.
         */
        for (Class<?> clazz : setOfMeterRegistryClasses) {
            if (MicrometerBackends.class.isAssignableFrom(clazz)) {
                try {
                    MicrometerBackends mb = (MicrometerBackends) clazz.newInstance();
                    MeterRegistry backendMeterRegistry = mb.produce();

                    /*
                     * Even if registry is on classpath, needs to have been enabled by config property, otherwise a null
                     * would be returned.
                     *
                     */
                    if (backendMeterRegistry != null) {
                        Metrics.globalRegistry.add(backendMeterRegistry);
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.logp(Level.FINE, CLASS_NAME, null,
                                    "MeterRegistry from {0} created and registered to the Micrometer global registry",
                                    clazz.getName());
                        }

                    } else {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.logp(Level.FINE, CLASS_NAME, null,
                                    "MeterRegistry from {0} is available on classpath, but was not configured to be enabled",
                                    clazz.getName());
                        }
                    }

                } catch (IllegalAccessException | InstantiationException e) {
                    // This shouldn't happen...
                    LOGGER.logp(Level.SEVERE, CLASS_NAME, null,
                            "Encountered exception while reflectively loading MicrometerBackends \"{0}\": {1}",
                            new Object[] { clazz.getName(), e });
                }
            } else {
                // This shouldn't happen.
                LOGGER.logp(Level.SEVERE, CLASS_NAME, null, "The class {0} is not compatible with {1} ",
                        new String[] { clazz.getName(), MicrometerBackends.class.getName() });
            }
        }

        meterRegistry = resolveMeterRegistry();
    }

    /*
     * For vendors to provide an AppNameResolver
     */
    public static void setAppNameResolver(ApplicationNameResolver anr) {
        appNameResolver = anr;
    }

    public static ApplicationNameResolver getAppNameResolver() {
        return appNameResolver;
    }

    public static Set<String> getRegistryScopeNames() {
        //return copy of 'registries' key-set containing scope names
        return new HashSet<String>(registries.keySet());
    }

    public static MetricRegistry getOrCreate(String scope) {
        /*
         * Check if there is a provided AppNameResolver
         */
        ApplicationNameResolver anr = (appNameResolver != null) ? appNameResolver : null;
        return getOrCreate(scope, anr);
    }

    public static MetricRegistry getOrCreate(String scope, ApplicationNameResolver anr) {
        final String METHOD_NAME = "getOrCreate";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.logp(Level.FINER, CLASS_NAME, METHOD_NAME, "Requested MetricRegistry of scope {0}", scope);
        }

        MetricRegistry metricRegistry = registries.computeIfAbsent(scope,
                t -> new LegacyMetricRegistryAdapter(scope, meterRegistry, anr));

        /*
         * Bind LegacyBaseMetrics to Base MP Metric Registry
         */
        if (!isBaseMetricsRegistered && scope.equals(MetricRegistry.BASE_SCOPE)) {
            new LegacyBaseMetrics().register(metricRegistry);
            isBaseMetricsRegistered = true;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME, "Base metrics registered");
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.logp(Level.FINER, CLASS_NAME, METHOD_NAME, "Returning MetricRegistry of scope \"{0}\"", scope);
        }
        return metricRegistry;
    }

    private static MeterRegistry resolveMeterRegistry() {
        final String METHOD_NAME = "resolveMeterRegistry";

        MeterRegistry meterRegistry;

        /*
         * If mp.metrics.prometheus.enabled is explicitly set to false Use SimpleMeterRegistry to associate
         * with MP Metric Registry.
         *
         * Otherwise, attempt to load PrometheusMeterRegistry. If is not on the classpath, then use
         * SimpleMeterRegistry
         */
        if (!Boolean.parseBoolean(ConfigProvider.getConfig()
                .getOptionalValue("mp.metrics.prometheus.enabled", String.class).orElse("true"))) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME,
                        "The MP Config value for mp.metrics.prometheus.enabled is false");
            }
            meterRegistry = new SimpleMeterRegistry();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME, "Simple MeterRegistry created");
            }

        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME,
                        "The MP Config value for mp.metrics.prometheus.enabled is true");
            }

            /*
             * The below Try block is equivalent to calling. meterRegistry = new
             * PrometheusMeterRegistry(customConfig); This is to address problems for runtimes that may need to
             * load SmallRye Metric Classes with reflection and that the Prometheus metric registry client
             * library is not provided on the class path
             */
            try {

                /*
                 * Try to load PrometheusConfig to see if we have the Prometheus Meter registry library on the class
                 * path
                 */
                Class<?> prometheusConfigClass = Class.forName(FQ_PROMETHEUS_CONFIG_PATH);

                /*
                 * Try to load the PrometheusMeterRegistry and create it
                 */
                Class<?> prometheusMetricRegistryClass = Class.forName(FQ_PROMETHEUS_METRIC_REGISTRY_PATH);

                Constructor<?> constructor = prometheusMetricRegistryClass.getConstructor(prometheusConfigClass);

                Object prometheusMeterRegistryInstance = constructor.newInstance(new MPPrometheusConfig());

                meterRegistry = (MeterRegistry) prometheusMeterRegistryInstance;
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME, "Prometheus MeterRegistry created");
                }
            } catch (ClassNotFoundException | SecurityException | IllegalArgumentException | IllegalAccessException
                    | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
                LOGGER.logp(Level.SEVERE, CLASS_NAME, METHOD_NAME,
                        "Encountered exception while reflectively loading Micrometer Prometheus classes: ", e);
                /*
                 * Default to simple meter registry otherwise. No Need to create a "MPSimpleMeterRegisty with scope
                 * field as scope was only used for the PrometheusExporter
                 */
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, CLASS_NAME, METHOD_NAME,
                            "Encountered exception while loading Prometheus MeterRegistry, defaulting to Simple MeterRegistry");
                }

                meterRegistry = new SimpleMeterRegistry();
            }
        }

        Metrics.addRegistry(meterRegistry);

        return meterRegistry;
    }

    /**
     * Drops a particular registry. If a reference to the same registry type is requested later, a new
     * empty registry will be created for that purpose.
     *
     * @param scope The scope of registry that should be dropped.
     */
    public static void drop(String scope) {
        registries.remove(scope);
    }

    /**
     * Drops all registries. If a reference to a registry is requested later, a new empty registry will
     * be created for that purpose.
     */
    public static void dropAll() {
        registries.clear();
    }

    /**
     * Returns true/false if registry with this scope exists
     *
     * @param scope name of scope
     * @return true/false if registry with this scope exists
     */
    public static boolean doesScopeExist(String scope) {
        return registries.containsKey(scope);
    }
}
