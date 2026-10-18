import com.discoveryscreen.widget.MotionMath;

public final class MotionMathTest {
    private MotionMathTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("MotionMathTest: " + msg);
    }

    private static void checkClose(float actual, float expected, float tolerance, String msg) {
        check(Math.abs(actual - expected) <= tolerance,
                msg + " (expected " + expected + " +/- " + tolerance + ", got " + actual + ")");
    }

    private interface Throwing {
        void run();
    }

    private static void checkThrows(Throwing body, String msg) {
        try {
            body.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("MotionMathTest: expected IllegalArgumentException for " + msg);
    }

    public static void run() {
        checkClose(MotionMath.progressFraction(0.2f, 0.0f, 0.5f), 0.4f, 1e-6f, "fraction inside window");
        checkClose(MotionMath.progressFraction(0.5f, 0.0f, 0.5f), 1.0f, 1e-6f, "fraction at upper bound");
        checkClose(MotionMath.progressFraction(0.9f, 0.0f, 0.5f), 1.0f, 1e-6f, "fraction past upper bound");
        checkClose(MotionMath.progressFraction(-0.1f, 0.0f, 0.5f), 0.0f, 1e-6f, "fraction below lower bound");
        checkClose(MotionMath.progressFraction(0.0f, 0.0f, 0.0f), 0.0f, 1e-6f, "degenerate window at zero");
        checkClose(MotionMath.progressFraction(5.0f, 5.0f, 5.0f), 1.0f, 1e-6f, "degenerate window above zero");
        checkThrows(() -> MotionMath.progressFraction(0.5f, 0.8f, 0.2f), "inverted window");

        checkClose(MotionMath.overshootInterpolation(0.0f, 100.0f), 0.0f, 1e-6f, "no overshoot at rest");
        checkClose(MotionMath.overshootInterpolation(100.0f, 100.0f), 1.0f, 1e-4f, "full overshoot at distance");
        checkClose(MotionMath.overshootInterpolation(-50.0f, 100.0f), 0.0f, 1e-6f, "negative progress clamps to zero");
        checkClose(MotionMath.overshootInterpolation(50.0f, 100.0f),
                (float) ((1.0d - Math.exp(-(Math.log(2.6666665f) * 0.5d))) * 1.6d), 1e-4f,
                "overshoot follows the exponential");
        float previous = -1.0f;
        for (float progress = 0.0f; progress <= 100.0f; progress += 10.0f) {
            float value = MotionMath.overshootInterpolation(progress, 100.0f);
            check(value >= previous, "overshoot is monotonic at progress " + progress);
            previous = value;
        }
        checkThrows(() -> MotionMath.overshootInterpolation(10.0f, 0.0f), "zero overshoot distance");

        checkClose(MotionMath.zoomOut(0.0f), 0.0f, 1e-6f, "zoom out starts at zero");
        checkClose(MotionMath.zoomOut(1.0f), 1.0f, 1e-4f, "zoom out ends at one");
        check(MotionMath.zoomOut(0.5f) > MotionMath.zoomOut(0.25f), "zoom out increases");

        checkClose(MotionMath.flingDurationSeconds(1000.0f, 1000.0f, 0.3f), 0.3f, 1e-6f, "full-size fling takes max length");
        checkClose(MotionMath.flingDurationSeconds(250.0f, 1000.0f, 0.3f), 0.15f, 1e-6f, "quarter distance, half duration");
        checkClose(MotionMath.flingDurationSeconds(-100.0f, 1000.0f, 0.3f),
                (float) (Math.sqrt(0.1d) * 0.3d), 1e-6f, "fling direction is irrelevant");

        checkClose(MotionMath.velocityFactor(0.0f, 250.0f, 3000.0f), 0.0f, 1e-6f, "velocity below minimum");
        checkClose(MotionMath.velocityFactor(250.0f, 250.0f, 3000.0f), 0.0f, 1e-6f, "velocity at minimum");
        checkClose(MotionMath.velocityFactor(3000.0f, 250.0f, 3000.0f), 1.0f, 1e-6f, "velocity at high threshold");
        checkClose(MotionMath.velocityFactor(4000.0f, 250.0f, 3000.0f), 1.0f, 1e-6f, "velocity past high threshold");
        checkClose(MotionMath.velocityFactor(1625.0f, 250.0f, 3000.0f), 0.5f, 1e-6f, "velocity at midpoint");
        checkClose(MotionMath.velocityFactor(-1625.0f, 250.0f, 3000.0f), 0.5f, 1e-6f, "velocity sign is irrelevant");

        checkClose(MotionMath.velocityFactorForSpeedUp(0.0f, 0.0f), 1.0f, 1e-6f, "speed-up disabled");
        checkClose(MotionMath.velocityFactorForSpeedUp(1500.0f, 0.3f), 0.5f, 1e-6f, "half of the speed-up range");
        checkClose(MotionMath.velocityFactorForSpeedUp(9000.0f, 0.3f), 1.0f, 1e-6f, "speed-up saturates");
        checkClose(MotionMath.velocityFactorForSpeedUp(-1500.0f, 0.3f), 0.5f, 1e-6f, "speed-up sign is irrelevant");

        checkClose(MotionMath.startGradient(0.0f, 0.35f, 1.0f), 0.75f, 1e-6f, "slow fling keeps the default gradient");
        checkClose(MotionMath.startGradient(1.0f, 0.35f, 1.0f), 1.0f / 0.35f, 1e-4f, "fast fling takes the curve gradient");
        checkClose(MotionMath.startGradient(0.5f, 0.35f, 1.0f),
                ((1.0f / 0.35f) * 0.5f) + (0.75f * 0.5f), 1e-4f, "gradient blends linearly");
        checkClose(MotionMath.curveX1(0.0f, 0.5f), 0.5f, 1e-6f, "curve x1 without velocity");
        checkClose(MotionMath.curveX1(1.0f, 0.5f), 0.0f, 1e-6f, "curve x1 at full velocity");
        checkClose(MotionMath.curveY1(0.25f, 0.75f), 0.1875f, 1e-6f, "curve y1 is x1 * gradient");
        checkClose(MotionMath.gradientDurationSeconds(0.75f, 400.0f, 1200.0f), 0.25f, 1e-6f, "gradient-matched duration");
        check(MotionMath.gradientDurationSeconds(0.75f, 400.0f, 0.0f) == Float.POSITIVE_INFINITY,
                "zero velocity has no velocity-limited duration");

        checkClose(MotionMath.pullPastLimit(0.0f, 200.0f), 0.0f, 1e-6f, "no pull without excess");
        checkClose(MotionMath.pullPastLimit(-40.0f, 200.0f), 0.0f, 1e-6f, "pull is one-directional");
        // Saturation is 0.1 * dimension at excess == dimension (the overshoot curve reaches 1.0 there).
        checkClose(MotionMath.pullPastLimit(200.0f, 200.0f), 20.0f, 1e-3f, "pull saturates at the rubber band limit");
        checkClose(MotionMath.pullPastLimit(1000.0f, 200.0f), 20.0f, 1e-3f, "pull cannot exceed saturation");
        check(MotionMath.pullPastLimit(20.0f, 200.0f) < 20.0f,
                "pull resists the finger instead of tracking it 1:1");
        float pullPrevious = -1.0f;
        for (float excess = 0.0f; excess <= 400.0f; excess += 20.0f) {
            float pull = MotionMath.pullPastLimit(excess, 200.0f);
            check(pull >= pullPrevious, "pull is monotonic at excess " + excess);
            pullPrevious = pull;
        }
        checkThrows(() -> MotionMath.pullPastLimit(10.0f, 0.0f), "zero drag dimension");

        checkClose(MotionMath.lerp(10.0f, 20.0f, 0.0f), 10.0f, 1e-6f, "lerp starts at from");
        checkClose(MotionMath.lerp(10.0f, 20.0f, 1.0f), 20.0f, 1e-6f, "lerp ends at to");
        checkClose(MotionMath.lerp(10.0f, 20.0f, 0.25f), 12.5f, 1e-6f, "lerp interpolates");

        check(MotionMath.clamp01(-2.0f) == 0.0f, "clamp01 floors at zero");
        check(MotionMath.clamp01(0.25f) == 0.25f, "clamp01 passes values through");
        check(MotionMath.clamp01(2.0f) == 1.0f, "clamp01 caps at one");

        System.out.println("MotionMathTest: PASS");
    }
}
