package io.smallrye.metrics.app;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

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