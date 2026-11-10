package de.loudbooster.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BoostGainTest {
    @Test
    public void convertsSliderStepsToMillibels() {
        assertEquals(0, BoostGain.toMillibels(0));
        assertEquals(50, BoostGain.toMillibels(1));
        assertEquals(200, BoostGain.toMillibels(4));
    }

    @Test
    public void keepsGainWithinSliderRangeEvenForInvalidInput() {
        assertEquals(0, BoostGain.toMillibels(-1));
        assertEquals(600, BoostGain.toMillibels(13));
        assertEquals(600, BoostGain.toMillibels(Integer.MAX_VALUE));
    }
}
