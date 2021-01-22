package io.smallrye.metrics.elementdesc.adapter.cdi;

import io.smallrye.metrics.elementdesc.BeanInfo;
import io.smallrye.metrics.elementdesc.adapter.BeanInfoAdapter;

public class CDIBeanInfoAdapter implements BeanInfoAdapter<Class<?>> {

    @Override
    public BeanInfo convert(Class<?> input) {
        return new CDIBeanInfo(input);
    }

}
