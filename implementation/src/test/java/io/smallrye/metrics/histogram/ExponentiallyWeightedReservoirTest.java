package io.smallrye.metrics.histogram;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;

import org.eclipse.microprofile.metrics.Histogram;
import org.junit.Assert;
import org.junit.Test;

import io.smallrye.metrics.app.ExponentiallyDecayingReservoir;
import io.smallrye.metrics.app.HistogramImpl;

public class ExponentiallyWeightedReservoirTest {

    @Test
    public void removeZeroWeightsInSamplesToPreventNaNInMeanValues() {
        final TestingClock clock = new TestingClock();
        final ExponentiallyDecayingReservoir reservoir = new ExponentiallyDecayingReservoir(1028, 0.015, clock);
        Histogram histogram = new HistogramImpl(reservoir);

        histogram.update(100);

        for (int i = 1; i < 48; i++) {
            clock.addHours(1);
            Assert.assertThat(reservoir.getSnapshot().getMean(), not(equalTo(Double.NaN)));
        }
    }

}
