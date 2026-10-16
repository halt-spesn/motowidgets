package com.discoveryscreen.widget;

import android.graphics.Path;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Interpolators ported verbatim from {@code com.android.app.animation.Interpolators}
 * (Launcher3 / MotoLauncher3) so the shelf motion matches the launcher.
 */
public final class Interpolators {
    private Interpolators() {
    }

    /** Material "emphasized" curve used for drag lifts and settles. */
    public static final Interpolator EMPHASIZED = createEmphasized();
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    public static final Interpolator DECELERATE = new DecelerateInterpolator();
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DECELERATE_3 = new DecelerateInterpolator(3.0f);
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();

    private static Interpolator createEmphasized() {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
}
