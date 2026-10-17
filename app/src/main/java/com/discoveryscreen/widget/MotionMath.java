package com.discoveryscreen.widget;

public final class MotionMath {
    /**
     * The drag band saturates at 0.1 times the dragged element's dimension.
     */
    public static final float RUBBER_BAND_FACTOR_NORMAL = 0.1f;

    private MotionMath() {
    }

    /**
     * Drag past a limit: the finger travels {@code excessPx} beyond the edge while the content
     * follows the recorded exponential overshoot curve instead of tracking 1:1.
     *
     * @param dimensionPx the moving element's size along the drag axis; at {@code excessPx} equal
     *                    to it the pull has saturated.
     * @param maxPullPx   offset reached at saturation.
     * @return visible offset past the limit, in {@code [0, maxPullPx]}.
     */
    public static float pullPastLimit(float excessPx, float dimensionPx, float maxPullPx) {
        if (excessPx <= 0.0f) {
            return 0.0f;
        }
        if (dimensionPx <= 0.0f) {
            throw new IllegalArgumentException("Drag dimension must be positive");
        }
        return overshootInterpolation(clamp01(excessPx / dimensionPx), 1.0f) * maxPullPx;
    }

    public static float pullPastLimit(float excessPx, float dimensionPx) {
        return pullPastLimit(excessPx, dimensionPx, RUBBER_BAND_FACTOR_NORMAL * dimensionPx);
    }

    public static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    /** @throws IllegalArgumentException if {@code upperBound} is below {@code lowerBound} */
    public static float progressFraction(float progress, float lowerBound, float upperBound) {
        if (upperBound < lowerBound) {
            throw new IllegalArgumentException("upperBound (" + upperBound
                    + ") must be greater than lowerBound (" + lowerBound + ")");
        }
        if (progress == lowerBound && progress == upperBound) {
            // A zero-width range is complete unless progress is zero.
            return progress != 0.0f ? 1.0f : 0.0f;
        }
        if (progress >= lowerBound) {
            if (progress > upperBound) {
                return 1.0f;
            }
            return (progress - lowerBound) / (upperBound - lowerBound);
        }
        return 0.0f;
    }

    /** Caller must clamp progress to distance; this function only clamps negative values. */
    public static float overshootInterpolation(float progress, float distance) {
        if (distance == 0.0f) {
            throw new IllegalArgumentException("Invalid values for overshoot");
        }
        float overshoot = ((float) (1.0d
                - Math.exp((-(((float) Math.log(2.6666665f)) / distance)) * progress))) * 1.6f;
        return overshoot < 0.0f ? 0.0f : overshoot;
    }

    public static float zoomOut(float fraction) {
        return (1.0f - (0.35f / (fraction + 0.35f))) / 0.7407408f;
    }

    public static float flingDurationSeconds(float distancePx, float sizePx, float maxLengthSeconds) {
        return (float) (Math.pow(Math.abs(distancePx) / sizePx, 0.5d) * maxLengthSeconds);
    }

    public static float velocityFactor(float velocityPxPerSecond, float minVelocityPxPerSecond,
            float highVelocityPxPerSecond) {
        return clamp01((Math.abs(velocityPxPerSecond) - minVelocityPxPerSecond)
                / (highVelocityPxPerSecond - minVelocityPxPerSecond));
    }

    public static float velocityFactorForSpeedUp(float velocityPxPerSecond, float speedUpFactor) {
        return speedUpFactor == 0.0f ? 1.0f : Math.min(Math.abs(velocityPxPerSecond) / 3000.0f, 1.0f);
    }

    public static float startGradient(float velocityFactor, float linearOutSlowInX2, float y2) {
        return ((y2 / linearOutSlowInX2) * velocityFactor) + (0.75f * (1.0f - velocityFactor));
    }

    public static float curveX1(float velocityFactor, float speedUpFactor) {
        return (1.0f - velocityFactor) * speedUpFactor;
    }

    public static float curveY1(float curveX1, float startGradient) {
        return curveX1 * startGradient;
    }

    public static float gradientDurationSeconds(float startGradient, float distancePx,
            float velocityPxPerSecond) {
        return (startGradient * Math.abs(distancePx)) / Math.abs(velocityPxPerSecond);
    }

    public static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        return value > 1.0f ? 1.0f : value;
    }
}
