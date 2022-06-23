/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.metrics.micrometer;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;

import com.netflix.spectator.atlas.AtlasConfig;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.appoptics.AppOpticsMeterRegistry;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import io.micrometer.elastic.ElasticConfig;
import io.micrometer.elastic.ElasticMeterRegistry;
import io.micrometer.ganglia.GangliaConfig;
import io.micrometer.ganglia.GangliaMeterRegistry;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.humio.HumioConfig;
import io.micrometer.humio.HumioMeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.kairos.KairosConfig;
import io.micrometer.kairos.KairosMeterRegistry;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.signalfx.SignalFxConfig;
import io.micrometer.signalfx.SignalFxMeterRegistry;
import io.micrometer.stackdriver.StackdriverConfig;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;

@Backend
public class MicrometerBackends {

    public static Class<?>[] classes() {
        return new Class<?>[] {
                AppOpticsBackendProducer.class,
                AtlasBackendProducer.class,
                DatadogBackendProducer.class,
                ElasticBackendProducer.class,
                GraphiteBackendProducer.class,
                GangliaBackendProducer.class,
                HumioBackendProducer.class,
                InfluxBackendProducer.class,
                JmxBackendProducer.class,
                KairosBackendProducer.class,
                NewRelicBackendProducer.class,
                PrometheusBackendProducer.class,
                SignalFxBackendProducer.class,
                StackdriverBackendProducer.class,
                StatsdBackendProducer.class,
                WavefrontBackendProducer.class
        };
    }

    @RequiresClass({ AppOpticsMeterRegistry.class, AppOpticsConfig.class })
    public static class AppOpticsBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.appoptics.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new AppOpticsMeterRegistry(new AppOpticsConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ AtlasMeterRegistry.class, AtlasConfig.class })
    public static class AtlasBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.atlas.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new AtlasMeterRegistry(new AtlasConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ DatadogMeterRegistry.class, DatadogConfig.class })
    public static class DatadogBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.datadog.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new DatadogMeterRegistry(new DatadogConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ DynatraceMeterRegistry.class, DynatraceConfig.class })
    public static class DynatraceBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.dynatrace.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new DynatraceMeterRegistry(new DynatraceConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ ElasticMeterRegistry.class, ElasticConfig.class })
    public static class ElasticBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.elastic.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new ElasticMeterRegistry(new ElasticConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ GangliaMeterRegistry.class, GangliaConfig.class })
    public static class GangliaBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.ganglia.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new GangliaMeterRegistry(new GangliaConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ GraphiteMeterRegistry.class, GraphiteConfig.class })
    public static class GraphiteBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.graphite.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new GraphiteMeterRegistry(new GraphiteConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ HumioMeterRegistry.class, HumioConfig.class })
    public static class HumioBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.humio.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new HumioMeterRegistry(new HumioConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ InfluxMeterRegistry.class, InfluxConfig.class })
    public static class InfluxBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.influx.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new InfluxMeterRegistry(new InfluxConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ JmxMeterRegistry.class, JmxConfig.class })
    public static class JmxBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean
                    .parseBoolean(config.getOptionalValue("microprofile.metrics.jmx.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new JmxMeterRegistry(new JmxConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ KairosMeterRegistry.class, KairosConfig.class })
    public static class KairosBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.kairos.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new KairosMeterRegistry(new KairosConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ NewRelicMeterRegistry.class, NewRelicConfig.class })
    public static class NewRelicBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.newrelic.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new NewRelicMeterRegistry(new NewRelicConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ PrometheusMeterRegistry.class, PrometheusConfig.class })
    public static class PrometheusBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.prometheus.enabled", String.class).orElse("true"))) {
                return null;
            }

            return new PrometheusMeterRegistry(new PrometheusConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            });
        }
    }

    @RequiresClass({ StackdriverMeterRegistry.class, StackdriverConfig.class })
    public static class StackdriverBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.stackdriver.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new StackdriverMeterRegistry(new StackdriverConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ SignalFxMeterRegistry.class, SignalFxConfig.class })
    public static class SignalFxBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.signalfx.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new SignalFxMeterRegistry(new SignalFxConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ StatsdMeterRegistry.class, StatsdConfig.class })
    public static class StatsdBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.statsd.enabled", String.class).orElse("false"))) {
                return null;
            }

            return new StatsdMeterRegistry(new StatsdConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    @RequiresClass({ WavefrontMeterRegistry.class, WavefrontConfig.class })
    public static class WavefrontBackendProducer {

        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            if (!Boolean.parseBoolean(
                    config.getOptionalValue("microprofile.metrics.wavefront.enabled", String.class).orElse("false"))) {
                return null;
            }
            return new WavefrontMeterRegistry(new WavefrontConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }

    public static class SimpleMeterRegistryProducer {
        @Inject
        private Config config;

        @Produces
        @Backend
        public MeterRegistry produce() {
            return new SimpleMeterRegistry(new SimpleConfig() {
                @Override
                public String get(final String propertyName) {
                    return config.getOptionalValue("microprofile.metrics." + propertyName, String.class)
                            .orElse(null);
                }
            }, io.micrometer.core.instrument.Clock.SYSTEM);
        }
    }
}
