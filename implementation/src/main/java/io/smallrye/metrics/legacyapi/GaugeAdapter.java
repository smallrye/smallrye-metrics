package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricType;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

interface GaugeAdapter<T extends Number> extends Gauge<T>, MeterHolder {

    GaugeAdapter<T> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope);

    static class DoubleFunctionGauge<S> implements GaugeAdapter<Double> {
        io.micrometer.core.instrument.Gauge gauge;

        final S obj;
        final ToDoubleFunction<S> f;

        /*
         * Have to hold on to meta data for get* methods. Due to multiple Prometheus
         * meter registries being registered to the global composite meter registry with
         * deny filters used, this can lead to a problem when the composite meter is
         * retrieving a value of the meter. It will chose the "first" meter registry
         * associated to the composite meter. This meter registry may have returned a
         * Noop meter (due it being denied). As a result, querying this composite meter
         * for a value can return a 0.
         * 
         * See SharedMetricRegistries.java for more information.
         * 
         * We do not save the prometheus meter regsitry's meter by itself as the
         * composite meter registry is needed for the getMeter() call which is used by
         * remove calls that interact with the global meter registry. Or it could be
         * that the composite meter is needed still to record data.
         * 
         * We do not save the prometheus meter registry's meter and the composite meter
         * registry together as we do not anticipate high usage of explicit get* calls
         * from the Metric API for the Metric so we save memory in favour of the process
         * overhead.
         */
        MeterRegistry registry;
        MetricDescriptor descriptor;
        String scope;
        Set<Tag> tagsSet = new HashSet<Tag>();

        DoubleFunctionGauge(S obj, ToDoubleFunction<S> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<Double> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {

            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            /*
             * Save metadata to this Adapter
             * for use with getValue()
             */
            this.registry = registry;
            this.descriptor = metricInfo;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), obj, f)
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .baseUnit(metadata.getUnit())
                    .strongReference(true)
                    .register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        /*
         * Due to registries that deny regsitration returning no-op and the chance of the composite meter
         * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
         */
        public Double getValue() {
            io.micrometer.core.instrument.Gauge promGauge = registry.find(descriptor.name()).tags(tagsSet).gauge();
            if (promGauge != null) {
                return promGauge.value();
            }
            return gauge.value();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }

    static class FunctionGauge<S, R extends Number> implements GaugeAdapter<R> {
        io.micrometer.core.instrument.Gauge gauge;

        final S obj;
        final Function<S, R> f;

        /*
         * Have to hold on to meta data for get* methods. Due to multiple Prometheus
         * meter registries being registered to the global composite meter registry with
         * deny filters used, this can lead to a problem when the composite meter is
         * retrieving a value of the meter. It will chose the "first" meter registry
         * associated to the composite meter. This meter registry may have returned a
         * Noop meter (due it being denied). As a result, querying this composite meter
         * for a value can return a 0.
         * 
         * See SharedMetricRegistries.java for more information.
         * 
         * We do not save the prometheus meter regsitry's meter by itself as the
         * composite meter registry is needed for the getMeter() call which is used by
         * remove calls that interact with the global meter registry. Or it could be
         * that the composite meter is needed still to record data.
         * 
         * We do not save the prometheus meter registry's meter and the composite meter
         * registry together as we do not anticipate high usage of explicit get* calls
         * from the Metric API for the Metric so we save memory in favour of the process
         * overhead.
         */
        MeterRegistry registry;
        MetricDescriptor descriptor;
        Set<Tag> tagsSet = new HashSet<Tag>();

        FunctionGauge(S obj, Function<S, R> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<R> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {
            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            /*
             * Save metadata to this Adapter
             * for use with getValue()
             */
            this.registry = registry;
            this.descriptor = metricInfo;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), obj, obj -> f.apply(obj).doubleValue())
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .baseUnit(metadata.getUnit())
                    .strongReference(true)
                    .register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        /*
         * Due to registries that deny regsitration returning no-op and the chance of the composite meter
         * obtaining the no-oped meter, we need to explicitly query the meter from the prom meter registry
         */
        public R getValue() {
            io.micrometer.core.instrument.Gauge promGauge = registry.find(descriptor.name()).tags(tagsSet).gauge();
            if (promGauge != null) {
                return (R) (Double) promGauge.value();
            }
            return (R) (Double) gauge.value();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }

    static class NumberSupplierGauge<T extends Number> implements GaugeAdapter<T> {
        io.micrometer.core.instrument.Gauge gauge;
        final Supplier<T> supplier;

        NumberSupplierGauge(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public GaugeAdapter<T> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {
            if (gauge == null || metadata.cleanDirtyMetadata()) {

                ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
                threadLocal.set(true);

                gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), (Supplier<Number>) supplier)
                        .description(metadata.getDescription())
                        .tags(metricInfo.tags())
                        .tags("scope", scope)
                        .baseUnit(metadata.getUnit())
                        .strongReference(true).register(Metrics.globalRegistry);
                threadLocal.set(false);
            }

            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public T getValue() {
            return supplier.get();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }
}
