package io.smallrye.metrics.elementdesc.adapter.cdi;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;

import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.elementdesc.adapter.MemberInfoAdapter;

public class CDIMemberInfoAdapter<T extends Member & AnnotatedElement> implements MemberInfoAdapter<T> {

    @Override
    public MemberInfo convert(T input) {
        return new CDIMemberInfo(input);
    }

}
