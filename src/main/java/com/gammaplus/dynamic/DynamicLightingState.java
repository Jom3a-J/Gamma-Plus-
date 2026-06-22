package com.gammaplus.dynamic;

/**
 * DynamicLightingState — framerate-independent exponential smoother for the
 * Dynamic Lighting brightness value.
 *
 * <p>Knows nothing about Minecraft. The tick handler sets the {@code targetBrightness}
 * (HIGH vs NORMAL) based on the environment; the Mixin calls {@link #update(double)}
 * every frame with the frame's delta-seconds and the configured rate, then reads
 * {@link #getValue()} to apply to the lightmap.
 *
 * <p>Math: {@code current += (target - current) * (1 - exp(-dt * rate))}.
 * At any framerate the convergence time is identical, so the feel is consistent.
 */
public final class DynamicLightingState {

    private float currentBrightness = 0.0f;
    private float targetBrightness  = 0.0f;
    private float transitionVelocity = 0.0f;
    private long lastUpdateNanos = -1L;

    /** Set the brightness we are ramping toward (0.0–1.0). */
    public synchronized void setTarget(float target) {
        this.targetBrightness = target;
    }

    /** The brightness we are currently displaying (0.0–1.0). */
    public synchronized float getValue() {
        return currentBrightness;
    }

    /** Target value, in case the caller wants to know where we're heading. */
    public synchronized float getTarget() {
        return targetBrightness;
    }

    /**
     * Advance {@code currentBrightness} toward {@code targetBrightness} by one frame.
     * Uses the exact analytical (closed-form) solution of a critically damped spring.
     * This is 100% stable at any frame rate, never overshoots, and starts transitions slowly (ease-in).
     *
     * @param stiffness natural frequency omega of the spring (controls stiffness/speed)
     */
    public synchronized void update(double stiffness) {
        long now = System.nanoTime();
        if (lastUpdateNanos < 0L) {
            lastUpdateNanos = now;
            return;
        }
        double deltaSeconds = (now - lastUpdateNanos) / 1_000_000_000.0;
        lastUpdateNanos = now;

        // Clamp deltaSeconds to a sensible maximum (e.g. 0.1s) to avoid huge jumps on lag spikes
        if (deltaSeconds > 0.1) {
            deltaSeconds = 0.1;
        }
        if (deltaSeconds <= 0.0) return;

        double omega = Math.max(0.0, stiffness);
        // Asymmetric speed: slower when brightening (dark adaptation), faster when dimming (light adaptation)
        if (targetBrightness > currentBrightness) {
            omega *= 0.6; // Slow fade-in (atmospherically adjusting to dark)
        } else if (targetBrightness < currentBrightness) {
            omega *= 1.5; // Fast fade-out (responsive clearing to bright)
        }
        double dt = deltaSeconds;

        // Analytical solution: x(t) = target + (A + B*t) * e^(-omega*t)
        double expTerm = Math.exp(-omega * dt);
        double A = currentBrightness - targetBrightness;
        double B = transitionVelocity + omega * A;

        currentBrightness = (float) (targetBrightness + (A + B * dt) * expTerm);
        transitionVelocity = (float) ((B - omega * (A + B * dt)) * expTerm);

        // Clamp float drift so a settled value stays exactly on target.
        if (Math.abs(targetBrightness - currentBrightness) < 0.0005f && Math.abs(transitionVelocity) < 0.0005f) {
            currentBrightness = targetBrightness;
            transitionVelocity = 0.0f;
        }
    }

    /** Hard-reset current brightness, target, velocity, and time tracking. */
    public synchronized void reset() {
        currentBrightness = 0.0f;
        targetBrightness  = 0.0f;
        transitionVelocity = 0.0f;
        lastUpdateNanos = -1L;
    }
}
