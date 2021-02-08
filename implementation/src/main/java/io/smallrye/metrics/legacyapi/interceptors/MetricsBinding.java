package io.smallrye.metrics.legacyapi.interceptors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.interceptor.InterceptorBinding;

@InterceptorBinding
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
// TODO: probably rename it to GaugeRegistrationBinding or something like that?!
public @interface MetricsBinding {
}
