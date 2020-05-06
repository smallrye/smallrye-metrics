package io.smallrye.metrics;

import org.eclipse.microprofile.metrics.MetricType;
import org.junit.Assert;
import org.junit.Test;

public class ExtendedMetadataBuilderTest {

    @Test
    public void testBuilder() {
        //given
        String displayName = "display name";
        String name = "name";
        String description = "description";
        String mbean = "mbean";
        String openMetricsKeyOverride = "openMetricsKeyOverride";
        String unit = "unit";

        //when
        ExtendedMetadata extendedMetadata = ExtendedMetadata.builder()
                .withType(MetricType.CONCURRENT_GAUGE)
                .withDisplayName(displayName)
                .withName(name)
                .withDescription(description)
                .withMbean(mbean)
                .withOpenMetricsKeyOverride(openMetricsKeyOverride)
                .withUnit(unit)
                .skipsScopeInOpenMetricsExportCompletely(false)
                .prependsScopeToOpenMetricsName(false)
                .multi(false)
                .build();

        //then
        Assert.assertEquals(displayName, extendedMetadata.getDisplayName());
        Assert.assertEquals(name, extendedMetadata.getName());
        Assert.assertEquals(mbean, extendedMetadata.getMbean());
        Assert.assertEquals(openMetricsKeyOverride, extendedMetadata.getOpenMetricsKeyOverride().get());
        Assert.assertEquals(unit, extendedMetadata.unit().get());
        Assert.assertEquals(description, extendedMetadata.description().get());
        Assert.assertFalse(extendedMetadata.isSkipsScopeInOpenMetricsExportCompletely());
        Assert.assertFalse(extendedMetadata.isMulti());
        Assert.assertFalse(extendedMetadata.prependsScopeToOpenMetricsName().get());
        Assert.assertEquals(MetricType.CONCURRENT_GAUGE, extendedMetadata.getTypeRaw());
    }
}
