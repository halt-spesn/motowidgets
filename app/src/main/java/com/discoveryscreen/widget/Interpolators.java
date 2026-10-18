package com.discoveryscreen.widget;

import android.graphics.Path;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

public final class Interpolators {
    private Interpolators() {
    }

    public static final Interpolator EMPHASIZED = createEmphasized();
    public static final Interpolator EMPHASIZED_COMPLEMENT = createEmphasizedComplement();
    public static final Interpolator EMPHASIZED_ACCELERATE =
            new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);
    public static final Interpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);
    public static final Interpolator STANDARD_ACCELERATE =
            new PathInterpolator(0.3f, 0.0f, 1.0f, 1.0f);
    public static final Interpolator STANDARD_DECELERATE =
            new PathInterpolator(0.0f, 0.0f, 0.0f, 1.0f);

    public static final Interpolator LEGACY = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator FAST_OUT_SLOW_IN = LEGACY;
    public static final Interpolator FAST_OUT_LINEAR_IN =
            new PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f);
    public static final Interpolator FAST_OUT_SLOW_IN_REVERSE =
            new PathInterpolator(0.8f, 0.0f, 0.6f, 1.0f);
    public static final Interpolator SLOW_OUT_LINEAR_IN =
            new PathInterpolator(0.8f, 0.0f, 1.0f, 1.0f);

    public static final Interpolator LEGACY_DECELERATE =
            new PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator LINEAR_OUT_SLOW_IN = LEGACY_DECELERATE;
    public static final Interpolator LINEAR = new LinearInterpolator();

    public static final Interpolator DECELERATE = new DecelerateInterpolator();
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DECELERATE_3 = new DecelerateInterpolator(3.0f);
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
    public static final Interpolator ACCELERATE = new AccelerateInterpolator();
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();

    public static final Interpolator CUSTOM_40_40 = new PathInterpolator(0.4f, 0.0f, 0.6f, 1.0f);
    public static final Interpolator ICON_OVERSHOT = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.4f);
    public static final Interpolator ICON_OVERSHOT_LESS =
            new PathInterpolator(0.4f, 0.0f, 0.2f, 1.1f);
    public static final Interpolator PANEL_CLOSE_ACCELERATED =
            new PathInterpolator(0.3f, 0.0f, 0.5f, 1.0f);
    public static final Interpolator CONTROL_STATE = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator TOUCH_RESPONSE = new PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f);
    public static final Interpolator TOUCH_RESPONSE_REVERSE =
            new PathInterpolator(0.9f, 0.0f, 0.7f, 1.0f);
    public static final Interpolator BACK_GESTURE = new PathInterpolator(0.1f, 0.1f, 0.0f, 1.0f);

    public static final Interpolator ALPHA_IN = new PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f);
    public static final Interpolator ALPHA_OUT = new PathInterpolator(0.0f, 0.0f, 0.8f, 1.0f);

    public static final Interpolator ZOOM_OUT = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return MotionMath.zoomOut(input);
        }
    };

    /** @throws IllegalArgumentException if {@code upperBound} is below {@code lowerBound} */
    public static float clampToProgress(Interpolator interpolator, float progress,
            float lowerBound, float upperBound) {
        return interpolator.getInterpolation(
                MotionMath.progressFraction(progress, lowerBound, upperBound));
    }

    /** @throws IllegalArgumentException if {@code distance} is zero */
    public static float getOvershootInterpolation(float progress, float distance) {
        return MotionMath.overshootInterpolation(progress, distance);
    }

    private static Interpolator createEmphasized() {
        Path path = new Path();
        path.moveTo(0.0f, 0.0f);
        path.cubicTo(0.05f, 0.0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1.0f, 1.0f, 1.0f);
        return new PathInterpolator(path);
    }

    private static Interpolator createEmphasizedComplement() {
        Path path = new Path();
        path.moveTo(0.0f, 0.0f);
        path.cubicTo(0.1217f, 0.0462f, 0.15f, 0.4686f, 0.1667f, 0.66f);
        path.cubicTo(0.1834f, 0.8878f, 0.1667f, 1.0f, 1.0f, 1.0f);
        return new PathInterpolator(path);
    }
}
