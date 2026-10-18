package com.discoveryscreen.widget;

import android.animation.ValueAnimator;
import android.view.Choreographer;

import java.util.WeakHashMap;

public final class Springs {
    public interface Target {
        float getValue();

        void setValue(float value);
    }

    private static final WeakHashMap<Target, Runner> RUNNERS = new WeakHashMap<>();

    private Springs() {
    }

    /** Uses a one-pixel threshold; use the overload for alpha, scale, or rotation. */
    public static void animateTo(Target target, SpringConfig config, float targetValue) {
        animateTo(target, config, targetValue, config.startVelocity,
                SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT);
    }

    /** Starts with the supplied velocity, typically a gesture's release velocity. */
    public static void animateTo(Target target, SpringConfig config, float targetValue,
            float startVelocity) {
        animateTo(target, config, targetValue, startVelocity, SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT);
    }

    public static void animateTo(Target target, SpringConfig config, float targetValue,
            float startVelocity, float minVisibleChange) {
        Runner running = RUNNERS.remove(target);
        float velocity = startVelocity;
        if (running != null) {
            velocity = running.solver.getVelocity();
            running.cancel();
        }
        if (ValueAnimator.getDurationScale() == 0.0f) {
            target.setValue(targetValue);
            return;
        }
        Runner runner = new Runner(target, config, targetValue, velocity, minVisibleChange);
        RUNNERS.put(target, runner);
        runner.start();
    }

    public static void cancel(Target target) {
        Runner running = RUNNERS.remove(target);
        if (running != null) {
            running.cancel();
        }
    }

    public static boolean isAnimating(Target target) {
        return RUNNERS.containsKey(target);
    }

    private static final class Runner {
        private final Target target;
        private final SpringSolver solver;
        private long lastFrameNanos;
        private boolean cancelled;

        private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (cancelled) {
                    return;
                }
                long deltaMillis = lastFrameNanos == 0L
                        ? 0L
                        : (frameTimeNanos - lastFrameNanos) / 1_000_000L;
                lastFrameNanos = frameTimeNanos;
                if (solver.advance(deltaMillis)) {
                    target.setValue(solver.getValue());
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    float end = solver.getFinalPosition();
                    solver.setValue(end);
                    target.setValue(end);
                    RUNNERS.remove(target);
                }
            }
        };

        Runner(Target target, SpringConfig config, float targetValue, float startVelocity,
                float minVisibleChange) {
            this.target = target;
            this.solver = new SpringSolver(config.withStartVelocity(startVelocity)
                    .withFinalPosition(targetValue), minVisibleChange);
            this.solver.setValue(target.getValue());
        }

        void start() {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }

        void cancel() {
            cancelled = true;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
    }
}
