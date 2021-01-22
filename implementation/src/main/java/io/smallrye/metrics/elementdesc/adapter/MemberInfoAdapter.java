package io.smallrye.metrics.elementdesc.adapter;

import io.smallrye.metrics.elementdesc.MemberInfo;

public interface MemberInfoAdapter<T> {

    MemberInfo convert(T input);

}
