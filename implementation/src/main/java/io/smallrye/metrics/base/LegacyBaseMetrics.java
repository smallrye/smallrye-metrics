package io.smallrye.metrics.base;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

import org.eclipse.microprofile.metrics.MetricUnits;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

/**
 * Base metrics from the MP Metrics 3.0 spec.
 */
public class LegacyBaseMetrics implements MeterBinder {

    // If we are running with GraalVM native mode, some metrics are not supported and will be skipped
    private final boolean nativeMode;

    private static final String THREAD_COUNT = "thread.count";
    private static final String THREAD_DAEMON_COUNT = "thread.daemon.count";
    private static final String THREAD_MAX_COUNT = "thread.max.count";
    private static final String CURRENT_LOADED_CLASS_COUNT = "classloader.loadedClasses.count";
    private static final String TOTAL_LOADED_CLASS_COUNT = "classloader.loadedClasses.total";
    private static final String TOTAL_UNLOADED_CLASS_COUNT = "classloader.unloadedClasses.total";
    private static final String JVM_UPTIME = "jvm.uptime";
    private static final String SYSTEM_LOAD_AVERAGE = "cpu.systemLoadAverage";
    private static final String CPU_AVAILABLE_PROCESSORS = "cpu.availableProcessors";
    private static final String PROCESS_CPU_LOAD = "cpu.processCpuLoad";
    private static final String PROCESS_CPU_TIME = "cpu.processCpuTime";
    private static final String MEMORY_COMMITTED_HEAP = "memory.committedHeap";
    private static final String MEMORY_MAX_HEAP = "memory.maxHeap";
    private static final String MEMORY_USED_HEAP = "memory.usedHeap";

    public LegacyBaseMetrics() {
        this.nativeMode = false;
    }

    public LegacyBaseMetrics(boolean nativeMode) {
        this.nativeMode = nativeMode;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        garbageCollectionMetrics(registry);
        classLoadingMetrics(registry);
        baseOperatingSystemMetrics(registry);
        threadingMetrics(registry);
        runtimeMetrics(registry);
        baseMemoryMetrics(registry);
    }

    private void garbageCollectionMetrics(MeterRegistry registry) {
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            FunctionCounter.builder("gc.total", gc, GarbageCollectorMXBean::getCollectionCount).description(
                    "Displays the total number of collections that have occurred." +
                            " This attribute lists -1 if the collection count is undefined for this collector.")
                    .tag("name",
                            gc.getName())
                    .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
            /*
             * Need to convert from milliseconds to seconds.
             */
            FunctionCounter.builder("gc.time", gc, gcObj -> (gcObj.getCollectionTime() / 1e+3)).description(
                    "Displays the approximate accumulated collection elapsed time in seconds. This attribute "
                            +
                            "displays -1 if the collection elapsed time is undefined for this collector. The Java "
                            +
                            "virtual machine implementation may use a high resolution timer to measure the "
                            +
                            "elapsed time. This attribute may display the same value even if the collection "
                            +
                            "count has been incremented if the collection elapsed time is very short.")
                    .tag("name",
                            gc.getName())
                    .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base")
                    .baseUnit(MetricUnits.SECONDS)
                    .register(registry);
        }
    }

    private void classLoadingMetrics(MeterRegistry registry) {
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        FunctionCounter.builder(TOTAL_LOADED_CLASS_COUNT, classLoadingMXBean, ClassLoadingMXBean::getTotalLoadedClassCount)
                .description(
                        "Displays the total number of classes that have been loaded since the Java virtual machine has started execution.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        FunctionCounter.builder(TOTAL_UNLOADED_CLASS_COUNT, classLoadingMXBean, ClassLoadingMXBean::getUnloadedClassCount)
                .description(
                        "Displays the total number of classes unloaded since the Java virtual machine has started execution.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(CURRENT_LOADED_CLASS_COUNT,
                classLoadingMXBean::getLoadedClassCount)
                .description("Displays the number of classes that are currently loaded in the Java virtual machine.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
    }

    private void baseOperatingSystemMetrics(MeterRegistry registry) {
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        Gauge.builder(SYSTEM_LOAD_AVERAGE,
                operatingSystemMXBean::getSystemLoadAverage)
                .description("Displays the system load average for the last minute. The system load average " +
                        "is the sum of the number of runnable entities queued to the available processors and the " +
                        "number of runnable entities running on the available processors averaged over a period of time. " +
                        "The way in which the load average is calculated is operating system specific but is typically a " +
                        "damped time-dependent average. If the load average is not available, a negative value is displayed. "
                        +
                        "This attribute is designed to provide a hint about the system load and may be queried frequently. "
                        +
                        "The load average may be unavailable on some platforms where it is expensive to implement this method.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(CPU_AVAILABLE_PROCESSORS, operatingSystemMXBean::getAvailableProcessors).description(
                "Displays the number of processors available to the Java virtual machine. This value may change during "
                        +
                        "a particular invocation of the virtual machine.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);

        // some metrics are only available in jdk internal class 'com.sun.management.OperatingSystemMXBean': cast to it.
        // com.sun.management.OperatingSystemMXBean is not available in SubstrateVM
        // the cast will fail for some JVM not derived from HotSpot (J9 for example) so we check if it is assignable to it
        if (!nativeMode
                && com.sun.management.OperatingSystemMXBean.class.isAssignableFrom(operatingSystemMXBean.getClass())) {
            try {
                com.sun.management.OperatingSystemMXBean internalOperatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) operatingSystemMXBean;
                Gauge.builder(PROCESS_CPU_LOAD,
                        internalOperatingSystemMXBean::getProcessCpuLoad)
                        .description("Displays  the \"recent cpu usage\" for the Java Virtual Machine process. " +
                                "This value is a double in the [0.0,1.0] interval. A value of 0.0 means that none of " +
                                "the CPUs were running threads from the JVM process during the recent period of time " +
                                "observed, while a value of 1.0 means that all CPUs were actively running threads from "
                                +
                                "the JVM 100% of the time during the recent period being observed. Threads from the JVM "
                                +
                                "include the application threads as well as the JVM internal threads. " +
                                "All values betweens 0.0 and 1.0 are possible depending of the activities going on in "
                                +
                                "the JVM process and the whole system. " +
                                "If the Java Virtual Machine recent CPU usage is not available, the method returns a negative value.")
                        .baseUnit(BaseUnits.PERCENT).tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
                //TODO: Probably change to RATIO base unit
                //TODO: Needs to be addressed in a MP Metrics PR first/discussion - do this later.

                /*
                 * Must convert from nanoseconds to seconds.
                 */
                Gauge.builder(PROCESS_CPU_TIME,
                        () -> (internalOperatingSystemMXBean.getProcessCpuTime() / 1e+9))
                        .description(
                                "Displays the CPU time used by the process on which the Java virtual machine is running in seconds.")
                        .baseUnit(MetricUnits.SECONDS).tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
            } catch (ClassCastException ignored) {
            }
        }
    }

    private void threadingMetrics(MeterRegistry registry) {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        Gauge.builder(THREAD_COUNT,
                thread::getThreadCount)
                .description("Displays the current number of live threads including both daemon and non-daemon threads")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(THREAD_DAEMON_COUNT, thread::getDaemonThreadCount)
                .description("Displays the current number of live daemon threads.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(THREAD_MAX_COUNT, thread::getPeakThreadCount)
                .description("Displays the peak live thread count since the Java virtual machine started or peak was " +
                        "reset. This includes daemon and non-daemon threads.")
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
    }

    private void runtimeMetrics(MeterRegistry registry) {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        /*
         * Need to convert from milliseconds to seconds.
         */
        Gauge.builder(JVM_UPTIME, () -> (runtimeMXBean.getUptime() / 1e+3))
                .description("Displays the time from the start of the Java virtual machine in seconds.")
                .baseUnit(MetricUnits.SECONDS)
                .tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
    }

    private void baseMemoryMetrics(MeterRegistry registry) {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        Gauge.builder(MEMORY_COMMITTED_HEAP,
                memoryMXBean.getHeapMemoryUsage()::getCommitted)
                .description("Displays the amount of memory in bytes that is committed for the Java virtual machine to use. " +
                        "This amount of memory is guaranteed for the Java virtual machine to use.")
                .baseUnit(BaseUnits.BYTES).tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(MEMORY_MAX_HEAP,
                memoryMXBean.getHeapMemoryUsage()::getMax)
                .description("Displays the maximum amount of heap memory in bytes that can be used for memory management. "
                        +
                        "This attribute displays -1 if the maximum heap memory size is undefined. This amount of memory is not "
                        +
                        "guaranteed to be available for memory management if it is greater than the amount of committed memory. "
                        +
                        "The Java virtual machine may fail to allocate memory even if the amount of used memory does " +
                        "not exceed this maximum size.")
                .baseUnit(BaseUnits.BYTES).tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
        Gauge.builder(MEMORY_USED_HEAP,
                memoryMXBean.getHeapMemoryUsage()::getUsed).description("Displays the amount of used heap memory in bytes.")
                .baseUnit(BaseUnits.BYTES).tag(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, "base").register(registry);
    }
}
