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

        DoubleFunctionGauge(S obj, ToDoubleFunction<S> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<Double> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {

            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), obj, f)
                    .description(metadata.getDescription()).tags(tagsSet).baseUnit(metadata.getUnit())
                    .strongReference(true).register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public Double getValue() {
            /*
             * We need to cheat to get value if Gauge is removed from registries and we still
             * have a ref to this (MP) Gauge, we should still be able to retrieve the value
             * instead of querying the meter registry for non-existing gauge.
             */
            return f.applyAsDouble(obj);
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

        FunctionGauge(S obj, Function<S, R> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<R> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {
            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            gauge = io.micrometer.core.instrument.Gauge
                    .builder(metricInfo.name(), obj, obj -> f.apply(obj).doubleValue())
                    .description(metadata.getDescription()).tags(tagsSet).baseUnit(metadata.getUnit())
                    .strongReference(true).register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public R getValue() {
            /*
             * We need to cheat. Micrometer returns a double. The generic parameter R is a
             * Number Or possibly any other boxed Number value. Can't cast to R... Since we
             * already have the object and function... just call it...
             * 
             * Also if Gauge is removed from registries and we still have a ref to this (MP)
             * Gauge, we should still be able to retrieve the value instead of querying the
             * meter registry for non-existing gauge.
             */
            return (R) f.apply(obj);
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
                        .description(metadata.getDescription()).tags(metricInfo.tags())
                        .tags(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope)
                        .baseUnit(metadata.getUnit()).strongReference(true).register(Metrics.globalRegistry);
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
