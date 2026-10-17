package com.discoveryscreen.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Property;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

/**
 * Target state for one view's transform: a target per property, each driven by a timed animator
 * or by a {@link Springs} spring. Adapted from SystemUI's {@code ViewState} and
 * {@code PropertyAnimator} (AOSP, Apache-2.0).
 *
 * <p>This exists because {@link View#animate()} mishandles both cases a gesture needs:</p>
 * <ul>
 *   <li>retargeting a running animation reuses the remaining time and carries its current velocity
 *       into the new trajectory, so motion does not abruptly restart;</li>
 *   <li>a delta under the property's minimum visible change is applied directly rather than
 *       animated ({@code SpringAnimation}'s per-property thresholds, see {@link SpringSolver}).</li>
 * </ul>
 */
public final class ViewStateAnimator {
    public static final int SCALE_X = 0;
    public static final int SCALE_Y = 1;
    public static final int TRANSLATION_X = 2;
    public static final int TRANSLATION_Y = 3;
    public static final int TRANSLATION_Z = 4;
    private static final int PROPERTY_COUNT = 5;

    private final View mView;
    private final PropertyEntry[] mEntries = new PropertyEntry[PROPERTY_COUNT];
    /** Properties the caller has given a target since the last drive. */
    private final boolean[] mTargeted = new boolean[PROPERTY_COUNT];
    private Runnable mEndAction;
    private Runnable mUpdateAction;

    public ViewStateAnimator(View view) {
        mView = view;
        mEntries[SCALE_X] = new PropertyEntry(view, View.SCALE_X, SpringSolver.MIN_VISIBLE_CHANGE_SCALE);
        mEntries[SCALE_Y] = new PropertyEntry(view, View.SCALE_Y, SpringSolver.MIN_VISIBLE_CHANGE_SCALE);
        mEntries[TRANSLATION_X] =
                new PropertyEntry(view, View.TRANSLATION_X, SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT);
        mEntries[TRANSLATION_Y] =
                new PropertyEntry(view, View.TRANSLATION_Y, SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT);
        mEntries[TRANSLATION_Z] =
                new PropertyEntry(view, View.TRANSLATION_Z, SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT);
    }

    public ViewStateAnimator scaleX(float target) {
        return target(SCALE_X, target);
    }

    public ViewStateAnimator scaleY(float target) {
        return target(SCALE_Y, target);
    }

    public ViewStateAnimator scale(float target) {
        return scaleX(target).scaleY(target);
    }

    public ViewStateAnimator translationX(float target) {
        return target(TRANSLATION_X, target);
    }

    public ViewStateAnimator translationY(float target) {
        return target(TRANSLATION_Y, target);
    }

    public ViewStateAnimator translationZ(float target) {
        return target(TRANSLATION_Z, target);
    }

    /**
     * Runs once the next drive has finished (or immediately, from a post, when it had nothing to
     * animate). A spring drive polls for its own completion, so the action is up to one frame
     * late there.
     */
    public ViewStateAnimator withEndAction(Runnable action) {
        mEndAction = action;
        return this;
    }

    /** Runs on every property update of the next drive, after the view has been moved. */
    public ViewStateAnimator withUpdateAction(Runnable action) {
        mUpdateAction = action;
        return this;
    }

    /**
     * Timed drive to the targets set since the last drive; the view's own {@code animate()} is
     * cancelled first. Properties this drive was not given a target for are left alone, so a drive
     * cannot drag a property another caller owns (e.g. the drag's own translation).
     */
    public void animateTo(long durationMs, Interpolator interpolator) {
        mView.animate().cancel();
        for (int i = 0; i < PROPERTY_COUNT; i++) {
            if (!mTargeted[i]) {
                continue;
            }
            PropertyEntry entry = mEntries[i];
            entry.cancelSpring();
            float target = entry.targetValue;
            if (entry.isInsignificant(target - entry.current())) {
                entry.cancelAnimator();
                entry.property.set(mView, target);
                continue;
            }
            if (entry.isAnimating()) {
                // Keep the elapsed fraction: shifting the start by the same delta as the target
                // leaves the distance still to travel consistent with the time still to run.
                entry.retarget(target, durationMs, interpolator);
                continue;
            }
            entry.startTimed(durationMs, interpolator);
        }
        clearTargets();
        if (!anyAnimatorRunning()) {
            runEndAction();
        }
    }

    /**
     * Spring drive to the targets set since the last drive, seeded with a gesture's release
     * velocity (px/s). The velocity is handed to the translation axes alone.
     */
    public void springTo(SpringConfig config, float velocityX, float velocityY) {
        mView.animate().cancel();
        for (int i = 0; i < PROPERTY_COUNT; i++) {
            if (!mTargeted[i]) {
                continue;
            }
            PropertyEntry entry = mEntries[i];
            entry.cancelAnimator();
            float target = entry.targetValue;
            if (entry.isInsignificant(target - entry.current())) {
                entry.cancelSpring();
                entry.property.set(mView, target);
                continue;
            }
            float velocity = i == TRANSLATION_X ? velocityX : i == TRANSLATION_Y ? velocityY : 0.0f;
            Springs.animateTo(entry, config, target, velocity, entry.minVisibleChange);
        }
        clearTargets();
        if (mEndAction != null) {
            pollSpringEnd();
        }
    }

    public void cancel() {
        mEndAction = null;
        mUpdateAction = null;
        for (PropertyEntry entry : mEntries) {
            entry.cancel();
        }
    }

    /** Drops the drivers of the named properties only, leaving the others running. */
    public void cancel(int... properties) {
        mEndAction = null;
        mUpdateAction = null;
        for (int property : properties) {
            mEntries[property].cancel();
        }
    }

    private boolean isAnimating() {
        if (anyAnimatorRunning()) {
            return true;
        }
        for (PropertyEntry entry : mEntries) {
            if (Springs.isAnimating(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyAnimatorRunning() {
        for (PropertyEntry entry : mEntries) {
            if (entry.isAnimating()) {
                return true;
            }
        }
        return false;
    }

    private ViewStateAnimator target(int property, float value) {
        mEntries[property].targetValue = value;
        mTargeted[property] = true;
        return this;
    }

    private void clearTargets() {
        for (int i = 0; i < PROPERTY_COUNT; i++) {
            mTargeted[i] = false;
        }
    }

    private void runUpdateAction() {
        Runnable action = mUpdateAction;
        if (action != null) {
            action.run();
        }
    }

    /**
     * The action is posted rather than run inline: a caller that chains steps ("settle, then
     * relax") must not have both steps collapse into one frame when nothing was animated.
     */
    private void runEndAction() {
        Runnable action = mEndAction;
        mEndAction = null;
        mUpdateAction = null;
        if (action != null) {
            mView.post(action);
        }
    }

    /** Springs have no completion callback, so the end action waits for the last running spring. */
    private void pollSpringEnd() {
        mView.postOnAnimation(() -> {
            if (isAnimating()) {
                pollSpringEnd();
            } else {
                runEndAction();
            }
        });
    }

    private final class PropertyEntry implements Springs.Target {
        private final View view;
        private final Property<View, Float> property;
        private final float minVisibleChange;
        private ValueAnimator animator;
        private float startValue;
        private float endValue;
        private float targetValue;
        private float velocityPerMs;
        private float lastAnimatedValue;
        private long lastAnimatedTimeMs;

        private PropertyEntry(View view, Property<View, Float> property, float minVisibleChange) {
            this.view = view;
            this.property = property;
            this.minVisibleChange = minVisibleChange;
            this.targetValue = property.get(view);
        }

        @Override
        public float getValue() {
            return property.get(view);
        }

        @Override
        public void setValue(float value) {
            property.set(view, value);
            runUpdateAction();
        }

        private float current() {
            return property.get(view);
        }

        private boolean isInsignificant(float delta) {
            return Math.abs(delta) <= minVisibleChange;
        }

        private boolean isAnimating() {
            return animator != null && animator.isRunning();
        }

        private void startTimed(long durationMs, Interpolator interpolator) {
            startTimed(durationMs, interpolator, 0f, false);
        }

        private void startTimed(long durationMs, Interpolator interpolator,
                float initialVelocityPerMs, boolean preserveVelocity) {
            cancelAnimator();
            this.startValue = current();
            this.endValue = targetValue;
            velocityPerMs = preserveVelocity ? initialVelocityPerMs : 0f;
            lastAnimatedValue = startValue;
            lastAnimatedTimeMs = android.os.SystemClock.uptimeMillis();
            ValueAnimator created = ValueAnimator.ofFloat(0f, 1f);
            created.setDuration(durationMs);
            // Interpolation is applied below so velocity can be measured in property units.
            created.setInterpolator(new LinearInterpolator());
            created.addUpdateListener(animation -> {
                if (animator == created) {
                    float fraction = (Float) animation.getAnimatedValue();
                    float value;
                    if (preserveVelocity) {
                        // Cubic Hermite interpolation keeps the current slope at the retarget
                        // point, then eases to zero velocity at the new destination.
                        float tangent = initialVelocityPerMs * durationMs;
                        float distance = endValue - startValue;
                        if (tangent * distance <= 0f) {
                            tangent = 0f;
                        } else {
                            tangent = Math.min(Math.abs(tangent), 3f * Math.abs(distance))
                                    * Math.signum(distance);
                        }
                        float t2 = fraction * fraction;
                        float t3 = t2 * fraction;
                        value = (2f * t3 - 3f * t2 + 1f) * startValue
                                + (t3 - 2f * t2 + fraction) * tangent
                                + (-2f * t3 + 3f * t2) * endValue;
                    } else {
                        value = startValue + (endValue - startValue) * interpolator
                                .getInterpolation(fraction);
                    }
                    property.set(view, value);
                    long now = android.os.SystemClock.uptimeMillis();
                    long elapsed = now - lastAnimatedTimeMs;
                    if (elapsed > 0L) {
                        velocityPerMs = (value - lastAnimatedValue) / elapsed;
                    }
                    lastAnimatedValue = value;
                    lastAnimatedTimeMs = now;
                    runUpdateAction();
                }
            });
            created.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator != created) {
                        return;
                    }
                    animator = null;
                    // Another property may still be mid-drive (a drive only moves the properties it
                    // was given a target for), so the end action waits for the whole view to stop.
                    if (!anyAnimatorRunning()) {
                        runEndAction();
                    }
                }
            });
            animator = created;
            created.start();
        }

        /** Retarget from the current value, preserving the remaining time and current velocity. */
        private void retarget(float target, long durationMs, Interpolator interpolator) {
            ValueAnimator running = animator;
            long remainingDurationMs = running == null
                    ? durationMs
                    : Math.max(0L, running.getDuration() - running.getCurrentPlayTime());
            float currentVelocity = velocityPerMs;
            targetValue = target;
            startTimed(remainingDurationMs, interpolator, currentVelocity, true);
        }

        private void cancelAnimator() {
            ValueAnimator running = animator;
            animator = null;
            if (running != null) {
                running.cancel();
            }
        }

        private void cancelSpring() {
            Springs.cancel(this);
        }

        private void cancel() {
            cancelAnimator();
            cancelSpring();
        }
    }
}
