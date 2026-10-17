package com.discoveryscreen.widget;

import android.animation.Animator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public final class FlingAnimationUtils {
    private static final String TAG = "FlingAnimationUtils";

    private static final float MIN_VELOCITY_DP_PER_SECOND = 250.0f;
    private static final float HIGH_VELOCITY_DP_PER_SECOND = 3000.0f;

    public static final class AnimatorProperties {
        public long duration;
        public Interpolator interpolator;
    }

    private final AnimatorProperties animatorProperties = new AnimatorProperties();
    private float cachedStartGradient = -1.0f;
    private float cachedVelocityFactor = -1.0f;
    private final float highVelocityPxPerSecond;
    private PathInterpolator interpolator;
    private final float linearOutSlowInX2;
    private final float maxLengthSeconds;
    private final float minVelocityPxPerSecond;
    private final float speedUpFactor;
    private final float y2;

    public FlingAnimationUtils(DisplayMetrics displayMetrics, float maxLengthSeconds) {
        this(displayMetrics, maxLengthSeconds, 0.0f, -1.0f, 1.0f);
    }

    /**
     * @param speedUpFactor 0 keeps the standard curve; higher values make fast flings start steeper
     * @param x2 negative to derive the control point from {@code speedUpFactor}
     */
    public FlingAnimationUtils(DisplayMetrics displayMetrics, float maxLengthSeconds,
            float speedUpFactor, float x2, float y2) {
        this.maxLengthSeconds = maxLengthSeconds;
        this.speedUpFactor = speedUpFactor;
        this.linearOutSlowInX2 = x2 < 0.0f
                ? (0.68f * speedUpFactor) + ((1.0f - speedUpFactor) * 0.35f)
                : x2;
        this.y2 = y2;
        float density = displayMetrics.density;
        this.minVelocityPxPerSecond = MIN_VELOCITY_DP_PER_SECOND * density;
        this.highVelocityPxPerSecond = density * HIGH_VELOCITY_DP_PER_SECOND;
    }

    public void apply(Animator animator, float currentPos, float endPos, float velocity, float size) {
        AnimatorProperties properties = getProperties(currentPos, endPos, velocity, size);
        animator.setDuration(properties.duration);
        animator.setInterpolator(properties.interpolator);
    }

    /**
     * Like {@link #apply} for a fling that dismisses its content: the curve is chosen so the content
     * leaves at the fling velocity rather than easing out.
     */
    public void applyDismissing(Animator animator, float currentPos, float endPos, float velocity,
            float size) {
        float diff = endPos - currentPos;
        float distance = Math.abs(diff);
        float absVelocity = Math.abs(velocity);
        float flingDuration = MotionMath.flingDurationSeconds(diff, size, maxLengthSeconds);
        float velocityFactor = MotionMath.velocityFactor(velocity, minVelocityPxPerSecond,
                highVelocityPxPerSecond);
        float controlY2 = (velocityFactor * 0.5f) + ((1.0f - velocityFactor) * 0.4f);
        PathInterpolator dismissingInterpolator = new PathInterpolator(0.0f, 0.0f, 0.5f, controlY2);
        float gradientDuration = MotionMath.gradientDurationSeconds(controlY2 / 0.5f, distance,
                absVelocity);

        if (gradientDuration <= flingDuration) {
            animatorProperties.interpolator = dismissingInterpolator;
            flingDuration = gradientDuration;
        } else if (absVelocity >= minVelocityPxPerSecond) {
            animatorProperties.interpolator = new InterpolatorInterpolator(
                    new VelocityInterpolator(flingDuration, absVelocity, distance),
                    dismissingInterpolator, Interpolators.LINEAR_OUT_SLOW_IN);
        } else {
            animatorProperties.interpolator = Interpolators.FAST_OUT_LINEAR_IN;
        }
        long durationMillis = (long) (flingDuration * 1000.0f);
        animatorProperties.duration = durationMillis;
        animator.setDuration(durationMillis);
        animator.setInterpolator(animatorProperties.interpolator);
    }

    public AnimatorProperties getProperties(float currentPos, float endPos, float velocity,
            float size) {
        float diff = endPos - currentPos;
        float distance = Math.abs(diff);
        float absVelocity = Math.abs(velocity);
        float flingDuration = MotionMath.flingDurationSeconds(diff, size, maxLengthSeconds);
        float velocityFactor = MotionMath.velocityFactorForSpeedUp(velocity, speedUpFactor);
        float startGradient = MotionMath.startGradient(velocityFactor, linearOutSlowInX2, y2);
        float gradientDuration = MotionMath.gradientDurationSeconds(startGradient, distance,
                absVelocity);

        Interpolator curve;
        if (Float.isNaN(velocityFactor)) {
            Log.e(TAG, "Invalid velocity factor", new Throwable());
            curve = Interpolators.LINEAR_OUT_SLOW_IN;
        } else {
            if (startGradient != cachedStartGradient || velocityFactor != cachedVelocityFactor) {
                float x1 = MotionMath.curveX1(velocityFactor, speedUpFactor);
                float y1 = MotionMath.curveY1(x1, startGradient);
                try {
                    interpolator = new PathInterpolator(x1, y1, linearOutSlowInX2, y2);
                    cachedStartGradient = startGradient;
                    cachedVelocityFactor = velocityFactor;
                } catch (IllegalArgumentException e) {
                    // PathInterpolator rejects out-of-range control points.
                    throw new IllegalArgumentException("Illegal path with x1=" + x1 + " y1=" + y1
                            + " x2=" + linearOutSlowInX2 + " y2=" + y2, e);
                }
            }
            curve = interpolator;
        }

        if (gradientDuration <= flingDuration) {
            animatorProperties.interpolator = curve;
            flingDuration = gradientDuration;
        } else if (absVelocity >= minVelocityPxPerSecond) {
            animatorProperties.interpolator = new InterpolatorInterpolator(
                    new VelocityInterpolator(flingDuration, absVelocity, distance), curve,
                    Interpolators.LINEAR_OUT_SLOW_IN);
        } else {
            animatorProperties.interpolator = Interpolators.FAST_OUT_SLOW_IN;
        }
        animatorProperties.duration = (long) (flingDuration * 1000.0f);
        return animatorProperties;
    }

    private static final class VelocityInterpolator implements Interpolator {
        private final float durationSeconds;
        private final float velocity;
        private final float diff;

        VelocityInterpolator(float durationSeconds, float velocity, float diff) {
            this.durationSeconds = durationSeconds;
            this.velocity = velocity;
            this.diff = diff;
        }

        @Override
        public float getInterpolation(float input) {
            return ((input * durationSeconds) * velocity) / diff;
        }
    }

    private static final class InterpolatorInterpolator implements Interpolator {
        private final VelocityInterpolator interpolator1;
        private final Interpolator interpolator2;
        private final Interpolator crossfader;

        InterpolatorInterpolator(VelocityInterpolator interpolator1, Interpolator interpolator2,
                Interpolator crossfader) {
            this.interpolator1 = interpolator1;
            this.interpolator2 = interpolator2;
            this.crossfader = crossfader;
        }

        @Override
        public float getInterpolation(float input) {
            float blend = crossfader.getInterpolation(input);
            return (interpolator2.getInterpolation(input) * blend)
                    + (interpolator1.getInterpolation(input) * (1.0f - blend));
        }
    }
}
