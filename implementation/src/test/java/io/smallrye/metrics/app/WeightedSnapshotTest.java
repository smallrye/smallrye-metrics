package io.smallrye.metrics.app;

import static org.junit.Assert.*;

import java.util.Collections;

import org.junit.Test;

/**
 * @author helloween
 */
public class WeightedSnapshotTest {

    @Test
    public void init() {
        WeightedSnapshot.WeightedSample sample = new WeightedSnapshot.WeightedSample(1L, 0.0);
        WeightedSnapshot snapshot = new WeightedSnapshot(Collections.singletonList(sample));
        assertFalse(Double.isNaN(snapshot.getMean()));
    }
}
