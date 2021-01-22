package io.smallrye.metrics.elementdesc.adapter;

import io.smallrye.metrics.elementdesc.BeanInfo;

public interface BeanInfoAdapter<T> {

    BeanInfo convert(T input);

}
