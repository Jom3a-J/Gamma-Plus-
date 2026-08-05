package com.gammaplus.dynamic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the darkness model that decides how hard Dynamic Lighting pushes. */
class DarknessCurveTest {

    /** Roughly what {@code getSkyDarken()} reaches at midnight in a clear overworld. */
    private static final float MIDNIGHT_DARKEN = 11.0f;

    @Nested
    @DisplayName("effective light")
    class EffectiveLight {

        @Test
        @DisplayName("open sky at noon is fully lit")
        void noonIsFullyLit() {
            assertEquals(15.0f, DarknessCurve.effectiveLight(0, 15, 0, 1.0f));
        }

        @Test
        @DisplayName("open sky at midnight is dim but not black")
        void midnightIsDim() {
            // This is the case the old model got wrong: sky light stays 15 at night, so it only
            // becomes meaningful once the darkening is subtracted.
            assertEquals(4.0f, DarknessCurve.effectiveLight(0, 15, MIDNIGHT_DARKEN, 1.0f));
        }

        @Test
        @DisplayName("an unlit cave is pitch black regardless of time")
        void unlitCaveIsBlack() {
            assertEquals(0.0f, DarknessCurve.effectiveLight(0, 0, 0, 1.0f));
            assertEquals(0.0f, DarknessCurve.effectiveLight(0, 0, MIDNIGHT_DARKEN, 1.0f));
        }

        @Test
        @DisplayName("a torch-lit cave reads as lit")
        void litCaveIsLit() {
            // The headline fix: sky light 0 used to mean maximum boost even under torches.
            assertEquals(14.0f, DarknessCurve.effectiveLight(14, 0, MIDNIGHT_DARKEN, 1.0f));
        }

        @Test
        @DisplayName("block light is never reduced by the time of day")
        void blockLightIgnoresTime() {
            assertEquals(10.0f, DarknessCurve.effectiveLight(10, 0, 0, 1.0f));
            assertEquals(10.0f, DarknessCurve.effectiveLight(10, 0, 15, 1.0f));
        }

        @Test
        @DisplayName("takes whichever source is brighter")
        void takesTheBrighterSource() {
            assertEquals(9.0f, DarknessCurve.effectiveLight(9, 15, MIDNIGHT_DARKEN, 1.0f), 0.001f);
            assertEquals(12.0f, DarknessCurve.effectiveLight(2, 15, 3.0f, 1.0f), 0.001f);
        }

        @Test
        @DisplayName("never goes negative, however dark the sky gets")
        void neverGoesNegative() {
            assertEquals(0.0f, DarknessCurve.effectiveLight(0, 4, 15, 1.0f));
        }

        @Test
        @DisplayName("stays within 0..15 across the whole input space")
        void staysInRange() {
            for (int block = 0; block <= 15; block++) {
                for (int sky = 0; sky <= 15; sky++) {
                    for (int darken = 0; darken <= 15; darken++) {
                        float light = DarknessCurve.effectiveLight(block, sky, darken, 1.0f);
                        assertTrue(light >= 0.0f && light <= DarknessCurve.MAX_LIGHT,
                                "block=" + block + " sky=" + sky + " darken=" + darken + " gave " + light);
                    }
                }
            }
        }

        @Test
        @DisplayName("responds continuously to a fractional sunset")
        void respondsToFractionalDarkening() {
            // Reading the float attribute instead of the rounded int is what makes dusk smooth,
            // so a fractional step must produce a fractional change.
            float earlier = DarknessCurve.effectiveLight(0, 15, 5.25f, 1.0f);
            float later = DarknessCurve.effectiveLight(0, 15, 5.75f, 1.0f);
            assertEquals(9.75f, earlier, 0.001f);
            assertEquals(9.25f, later, 0.001f);
            assertTrue(later < earlier, "a darker sky must give less effective light");
        }
    }

    @Nested
    @DisplayName("darkness")
    class Darkness {

        @Test
        @DisplayName("at or below the dark threshold is full boost")
        void atOrBelowDarkThresholdIsFull() {
            assertEquals(1.0f, DarknessCurve.darkness(0, 4, 12));
            assertEquals(1.0f, DarknessCurve.darkness(4, 4, 12));
        }

        @Test
        @DisplayName("at or above the bright threshold is no boost")
        void atOrAboveBrightThresholdIsNone() {
            assertEquals(0.0f, DarknessCurve.darkness(12, 4, 12));
            assertEquals(0.0f, DarknessCurve.darkness(15, 4, 12));
        }

        @Test
        @DisplayName("halfway between thresholds gives a quarter boost, not half")
        void appliesPerceptualCurve() {
            // Squared, so mild dimness barely registers.
            assertEquals(0.25f, DarknessCurve.darkness(8, 4, 12), 0.001f);
        }

        @Test
        @DisplayName("more light is never more darkness")
        void isMonotonic() {
            float previous = Float.MAX_VALUE;
            for (int light = 0; light <= 15; light++) {
                float darkness = DarknessCurve.darkness(light, 4, 12);
                assertTrue(darkness <= previous, "darkness rose as light rose, at " + light);
                previous = darkness;
            }
        }

        @Test
        @DisplayName("equal thresholds do not divide by zero")
        void equalThresholdsAreSafe() {
            for (int light = 0; light <= 15; light++) {
                float darkness = DarknessCurve.darkness(light, 8, 8);
                assertTrue(Float.isFinite(darkness), "light " + light + " produced " + darkness);
                assertTrue(darkness == 0.0f || darkness == 1.0f, "should collapse to a step");
            }
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 4, 7, 11, 14})
        @DisplayName("stays within 0..1 for any threshold pair")
        void staysInRange(int darkLevel) {
            for (int brightLevel = darkLevel + 1; brightLevel <= 15; brightLevel++) {
                for (int light = 0; light <= 15; light++) {
                    float darkness = DarknessCurve.darkness(light, darkLevel, brightLevel);
                    assertTrue(darkness >= 0.0f && darkness <= 1.0f,
                            "light " + light + " in [" + darkLevel + "," + brightLevel + "] gave " + darkness);
                }
            }
        }
    }

    @Nested
    @DisplayName("torch influence")
    class TorchInfluence {

        /** Block light a torch puts on the block beside it. */
        private static final float TORCH = 13.0f;

        @Test
        @DisplayName("zero influence ignores torches entirely")
        void zeroInfluenceIgnoresTorches() {
            // The opt-out: detection falls back to sky access and time alone.
            assertEquals(0.0f, DarknessCurve.effectiveLight(15, 0, 0, 0.0f));
            assertEquals(4.0f, DarknessCurve.effectiveLight(15, 15, MIDNIGHT_DARKEN, 0.0f));
        }

        @Test
        @DisplayName("full influence lets one torch cancel the boost")
        void fullInfluenceCancelsBoost() {
            // The behaviour that prompted this setting: a single torch reads above any useful
            // bright threshold, so the boost drops to nothing.
            float light = DarknessCurve.effectiveLight(TORCH, 0, MIDNIGHT_DARKEN, 1.0f);
            assertEquals(0.0f, DarknessCurve.darkness(light, 4, 12), 0.001f);
        }

        @Test
        @DisplayName("half influence leaves a torch-lit cave near half boost")
        void halfInfluenceKeepsSomeBoost() {
            float light = DarknessCurve.effectiveLight(TORCH, 0, MIDNIGHT_DARKEN, 0.5f);
            assertEquals(6.5f, light, 0.001f);
            assertEquals(0.47f, DarknessCurve.darkness(light, 4, 12), 0.02f);
        }

        @Test
        @DisplayName("a fully lit room still gets less than a torch-lit one")
        void brighterAreasStillGetLess() {
            float torchLit = DarknessCurve.darkness(
                    DarknessCurve.effectiveLight(TORCH, 0, MIDNIGHT_DARKEN, 0.5f), 4, 12);
            float fullyLit = DarknessCurve.darkness(
                    DarknessCurve.effectiveLight(15, 0, MIDNIGHT_DARKEN, 0.5f), 4, 12);
            assertTrue(fullyLit < torchLit,
                    "more placed light must still mean less boost (" + fullyLit + " vs " + torchLit + ")");
        }

        @Test
        @DisplayName("more influence never means more boost")
        void isMonotonicInInfluence() {
            float previous = Float.MAX_VALUE;
            for (int step = 0; step <= 10; step++) {
                float darkness = DarknessCurve.darkness(
                        DarknessCurve.effectiveLight(TORCH, 0, MIDNIGHT_DARKEN, step / 10.0f), 4, 12);
                assertTrue(darkness <= previous, "boost rose as influence rose, at " + step);
                previous = darkness;
            }
        }

        @Test
        @DisplayName("influence never rescues an unlit cave")
        void unlitCaveIsUnaffected() {
            for (int step = 0; step <= 10; step++) {
                assertEquals(0.0f, DarknessCurve.effectiveLight(0, 0, 0, step / 10.0f),
                        "no block light to scale, so nothing should change");
            }
        }

        @Test
        @DisplayName("out-of-range influence is clamped rather than inverted")
        void clampsOutOfRangeInfluence() {
            assertEquals(DarknessCurve.effectiveLight(TORCH, 0, 0, 1.0f),
                    DarknessCurve.effectiveLight(TORCH, 0, 0, 4.0f), 0.001f);
            assertEquals(DarknessCurve.effectiveLight(TORCH, 0, 0, 0.0f),
                    DarknessCurve.effectiveLight(TORCH, 0, 0, -1.0f), 0.001f);
        }
    }

    @Nested
    @DisplayName("end to end, at the defaults")
    class EndToEnd {

        private static float darknessFor(float block, float sky, float darken) {
            return DarknessCurve.darkness(DarknessCurve.effectiveLight(block, sky, darken, 1.0f), 4, 12);
        }

        @Test
        @DisplayName("unlit cave gets the full boost")
        void unlitCave() {
            assertEquals(1.0f, darknessFor(0, 0, 0));
        }

        @Test
        @DisplayName("torch-lit base gets none")
        void litBase() {
            assertEquals(0.0f, darknessFor(14, 0, MIDNIGHT_DARKEN));
        }

        @Test
        @DisplayName("midday gets none")
        void midday() {
            assertEquals(0.0f, darknessFor(0, 15, 0));
        }

        @Test
        @DisplayName("surface midnight gets the full boost")
        void surfaceMidnight() {
            // Effective light 4 sits exactly on the dark threshold.
            assertEquals(1.0f, darknessFor(0, 15, MIDNIGHT_DARKEN));
        }

        @Test
        @DisplayName("dusk ramps up gradually rather than snapping")
        void duskRampsGradually() {
            // The old model saturated at skyDarken 3 and stayed there; this one should still be
            // climbing well past that, and strictly increasing throughout.
            float previous = -1.0f;
            for (float darken = 0.0f; darken <= MIDNIGHT_DARKEN; darken += 0.5f) {
                float darkness = darknessFor(0, 15, darken);
                assertTrue(darkness >= previous, "dusk went backwards at darken=" + darken);
                previous = darkness;
            }
            assertTrue(darknessFor(0, 15, 3.0f) < 0.35f, "early dusk should still be gentle");
            assertTrue(darknessFor(0, 15, 7.0f) < darknessFor(0, 15, 9.0f), "should keep climbing after dusk");
        }
    }
}
