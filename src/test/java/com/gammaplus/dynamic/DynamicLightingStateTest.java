package com.gammaplus.dynamic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the critically damped spring in {@link DynamicLightingState}.
 *
 * <p>Every test drives {@code update(stiffness, nanos)} with synthetic timestamps, so the results
 * are exact rather than dependent on how fast the test machine happens to run.
 */
class DynamicLightingStateTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final double RATE = 2.0;

    /** Advances the smoother for {@code seconds} at a fixed frame rate, returning the end value. */
    private static float simulate(DynamicLightingState state, double seconds, int fps, double rate) {
        long now = 1_000_000_000L; // Arbitrary non-zero epoch.
        state.update(rate, now);   // First call only establishes the time base.
        long step = NANOS_PER_SECOND / fps;
        for (int i = 0; i < (int) Math.round(seconds * fps); i++) {
            now += step;
            state.update(rate, now);
        }
        return state.getValue();
    }

    @Test
    @DisplayName("starts at zero and the first update only establishes the time base")
    void firstUpdateDoesNotMoveValue() {
        DynamicLightingState state = new DynamicLightingState();
        state.setTarget(1.0f);

        assertEquals(0.0f, state.getValue());
        state.update(RATE, 5_000_000_000L);
        assertEquals(0.0f, state.getValue(),
                "the first update has no previous timestamp, so it cannot integrate yet");
    }

    @Test
    @DisplayName("converges onto the target and settles exactly")
    void convergesExactlyOntoTarget() {
        DynamicLightingState state = new DynamicLightingState();
        state.setTarget(1.0f);

        float value = simulate(state, 30.0, 60, RATE);

        assertEquals(1.0f, value, 0.0f, "should snap exactly onto target once drift is negligible");
    }

    @Test
    @DisplayName("never overshoots the target")
    void neverOvershoots() {
        DynamicLightingState state = new DynamicLightingState();
        state.setTarget(1.0f);

        long now = NANOS_PER_SECOND;
        state.update(RATE, now);
        for (int i = 0; i < 600; i++) {
            now += NANOS_PER_SECOND / 60;
            state.update(RATE, now);
            assertTrue(state.getValue() <= 1.0f,
                    "critically damped means no overshoot, saw " + state.getValue());
            assertTrue(state.getValue() >= 0.0f, "should not dip below the start value");
        }
    }

    @Test
    @DisplayName("reaches the same place at 30fps and 240fps")
    void isFramerateIndependent() {
        DynamicLightingState slow = new DynamicLightingState();
        DynamicLightingState fast = new DynamicLightingState();
        slow.setTarget(1.0f);
        fast.setTarget(1.0f);

        float atThirty = simulate(slow, 1.5, 30, RATE);
        float atTwoForty = simulate(fast, 1.5, 240, RATE);

        assertEquals(atThirty, atTwoForty, 0.01f,
                "convergence must depend on elapsed time, not on how many frames elapsed");
    }

    @Test
    @DisplayName("brightens more slowly than it dims, mimicking eye adaptation")
    void brighteningIsSlowerThanDimming() {
        DynamicLightingState brightening = new DynamicLightingState();
        brightening.setTarget(1.0f);
        float gained = simulate(brightening, 0.5, 60, RATE);

        // Same journey in reverse: start settled at 1.0, then head back to 0.0.
        DynamicLightingState dimming = new DynamicLightingState();
        dimming.setTarget(1.0f);
        simulate(dimming, 30.0, 60, RATE); // settle at 1.0
        dimming.setTarget(0.0f);
        long now = 100 * NANOS_PER_SECOND;
        dimming.update(RATE, now);
        for (int i = 0; i < 30; i++) { // 0.5s at 60fps
            now += NANOS_PER_SECOND / 60;
            dimming.update(RATE, now);
        }
        float lost = 1.0f - dimming.getValue();

        assertTrue(lost > gained,
                "dimming should cover more ground in the same time (got " + lost + " vs " + gained + ")");
    }

    @Test
    @DisplayName("a lag spike is clamped instead of snapping the brightness")
    void clampsHugeFrameDeltas() {
        DynamicLightingState spiked = new DynamicLightingState();
        spiked.setTarget(1.0f);
        long now = NANOS_PER_SECOND;
        spiked.update(RATE, now);
        spiked.update(RATE, now + 10 * NANOS_PER_SECOND); // a 10 second stall

        DynamicLightingState capped = new DynamicLightingState();
        capped.setTarget(1.0f);
        long other = NANOS_PER_SECOND;
        capped.update(RATE, other);
        capped.update(RATE, other + NANOS_PER_SECOND / 10); // the 0.1s clamp

        assertEquals(capped.getValue(), spiked.getValue(), 1.0e-6f,
                "a 10s delta must be treated as the 0.1s maximum, not integrated in full");
        assertTrue(spiked.getValue() < 1.0f, "a single stalled frame must not jump straight to target");
    }

    @Test
    @DisplayName("a higher rate converges faster")
    void higherRateConvergesFaster() {
        DynamicLightingState gentle = new DynamicLightingState();
        DynamicLightingState snappy = new DynamicLightingState();
        gentle.setTarget(1.0f);
        snappy.setTarget(1.0f);

        float slowValue = simulate(gentle, 0.5, 60, 1.0);
        float fastValue = simulate(snappy, 0.5, 60, 8.0);

        assertTrue(fastValue > slowValue,
                "rate 8.0 should outpace rate 1.0 (" + fastValue + " vs " + slowValue + ")");
    }

    @Test
    @DisplayName("handles the full 0-15 gamma range, not just 0-1")
    void supportsFullGammaRange() {
        DynamicLightingState state = new DynamicLightingState();
        state.setTarget(15.0f);

        float value = simulate(state, 30.0, 60, RATE);

        assertEquals(15.0f, value, 0.0f, "targets share the gamma range, which reaches 15.0");
    }

    @Test
    @DisplayName("reset clears value, target and the time base")
    void resetClearsEverything() {
        DynamicLightingState state = new DynamicLightingState();
        state.setTarget(1.0f);
        simulate(state, 30.0, 60, RATE);
        assertEquals(1.0f, state.getValue());

        state.reset();

        assertEquals(0.0f, state.getValue());
        assertEquals(0.0f, state.getTarget());
        // Time base cleared too: the next update re-establishes it and must not integrate a
        // delta measured against the pre-reset timestamp.
        state.setTarget(1.0f);
        state.update(RATE, 9_999_999_999L);
        assertEquals(0.0f, state.getValue());
    }
}
