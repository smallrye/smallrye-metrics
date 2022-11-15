package io.smallrye.metrics;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * associated to an "underlying" Micrometer Prometheus MeterRegistry. Each of these Prometheus Meter
 * Registries are registered under the default Micrometer global composite meter registry. With this
 * implementation any creation/retrieval is negotiated with the global composite meter registry.
 * 
 * To ensure that the different "scoped" MetricRegistry to MeterRegistry contain their own
 * appropriate metrics/meters a Meter Filter is provided to each Prometheus MeterRegistry. This
 * filter makes use of a {@code ThreadLocal<Boolean>} to ensure that appropriate metrics/meters are
 * registered/retrieved from the appropriate registry.
 * 
 * The {@code ThreadLocal<Boolean>} will be set to false to gate registration/retrieval. And it will
 * be set to true before interacting with the global registry. A
 * {@code Map<String, ThreadLocal<Boolean>>} holds a mapping between the scope and ThreadLocal. This
 * map is interrogated when the MP MetricRegistry shim interacts with the global registry.
 * 
 */
public class SharedMetricRegistries {

    protected static final String GLOBAL_TAG_MALFORMED_EXCEPTION = "Malformed list of Global Tags. Tag names "
            + "must match the following regex [a-zA-Z_][a-zA-Z0-9_]*." + " Global Tag values must not be empty."
            + " Global Tag values MUST escape equal signs `=` and commas `,`" + " with a backslash `\\` ";

    protected static final String GLOBAL_TAGS_VARIABLE = "mp.metrics.tags";

    private static final String FQ_PROMETHEUS_CONFIG_PATH = "io.micrometer.prometheus.PrometheusConfig";
    private static final String FQ_PROMETHEUS_METRIC_REGISTRY_PATH = "io.micrometer.prometheus.PrometheusMeterRegistry";

    private static final Map<String, MetricRegistry> registries = new ConcurrentHashMap<>();
    private static boolean isBaseMetricsRegistered = false;

    private static MeterRegistry meterRegistry;
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
                // Do nothing
                // No need to log, fail silently
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
                    }

                } catch (IllegalAccessException | InstantiationException e) {
                    // This shouldn't happen...
                    // TODO: but we should log about it if it ever does happen..
                }
            } else {
                // This shouldn't happen.
                // TODO: but we should log about it if it ever does happen..
            }
        }

        meterRegistry = resolveMeterRegistry();
    }

    public static MetricRegistry getOrCreate(String scope) {
        return getOrCreate(scope, null);
    }

    // FIXME: cheap way of passing in the ApplicationNameResolvr from vendor code to the MetricRegistry
    public static MetricRegistry getOrCreate(String scope, ApplicationNameResolver appNameResolver) {

        MetricRegistry metricRegistry = registries.computeIfAbsent(scope,
                t -> new LegacyMetricRegistryAdapter(scope, meterRegistry, appNameResolver));

        /*
         * Bind LegacyBaseMetrics to Base MP Metric Registry
         */
        if (!isBaseMetricsRegistered && scope.equals(MetricRegistry.BASE_SCOPE)) {
            new LegacyBaseMetrics().register(metricRegistry);
            isBaseMetricsRegistered = true;
        }

        return metricRegistry;
    }

    private static MeterRegistry resolveMeterRegistry() {

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
            meterRegistry = new SimpleMeterRegistry();
        } else {

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

            } catch (ClassNotFoundException | SecurityException | IllegalArgumentException | IllegalAccessException
                    | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
                // TODO: We really don't care about exception being thrown, but should log about it anyways
                // (finest?)
                /*
                 * Default to simple meter registry otherwise. No Need to create a "MPSimpleMeterRegisty with scope
                 * field as scope was only used for the PrometheusExporter
                 */
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
