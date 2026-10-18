import com.discoveryscreen.widget.SpringConfig;
import com.discoveryscreen.widget.SpringSolver;

public final class SpringSolverTest {
    private SpringSolverTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("SpringSolverTest: " + msg);
    }

    private static void checkClose(double actual, double expected, double tolerance, String msg) {
        check(Math.abs(actual - expected) <= tolerance,
                msg + " (expected " + expected + " +/- " + tolerance + ", got " + actual + ")");
    }

    private interface Throwing {
        void run();
    }

    private static void checkThrows(Throwing body, String msg) {
        try {
            body.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("SpringSolverTest: expected IllegalStateException for " + msg);
    }

    private static long settle(SpringSolver solver, long frameMs, long maxMs, Peak peak) {
        long elapsed = 0L;
        while (solver.advance(frameMs)) {
            elapsed += frameMs;
            if (peak != null) peak.observe(solver.getValue());
            if (elapsed > maxMs) throw new AssertionError(
                    "SpringSolverTest: spring did not settle within " + maxMs + "ms");
        }
        return elapsed;
    }

    private static final class Peak {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        void observe(float value) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
    }

    public static void run() {
        check(SpringSolver.THRESHOLD_MULTIPLIER == 0.75f, "threshold multiplier is 0.75");
        check(SpringSolver.VELOCITY_THRESHOLD_FACTOR == 62.5f, "velocity threshold factor is 62.5");
        check(SpringSolver.MIN_VISIBLE_CHANGE_DEFAULT == 1.0f, "default min visible change is 1px");
        check(SpringSolver.MIN_VISIBLE_CHANGE_ALPHA == 0.00390625f, "alpha min visible change is 1/256");
        check(SpringSolver.MIN_VISIBLE_CHANGE_SCALE == 0.002f, "scale min visible change is 0.002");
        check(SpringSolver.MIN_VISIBLE_CHANGE_ROTATION == 0.1f, "rotation min visible change is 0.1");

        SpringSolver thresholds = new SpringSolver(SpringConfig.DEFAULT);
        checkClose(thresholds.getValueThreshold(), 0.75d, 1e-9d, "default value threshold");
        checkClose(thresholds.getVelocityThreshold(), 46.875d, 1e-9d, "default velocity threshold");
        SpringSolver alpha = new SpringSolver(SpringConfig.DEFAULT, SpringSolver.MIN_VISIBLE_CHANGE_ALPHA);
        checkClose(alpha.getValueThreshold(), 0.0029296875d, 1e-12d, "alpha value threshold");

        SpringSolver bouncy = new SpringSolver(SpringConfig.DEFAULT.withFinalPosition(100.0f));
        Peak bouncyPeak = new Peak();
        long bouncySettle = settle(bouncy, 8L, 3000L, bouncyPeak);
        check(bouncy.isAtEquilibrium(), "bouncy spring rests");
        check(bouncyPeak.max > 100.0f, "bouncy spring overshoots its target");
        check(bouncyPeak.max < 125.0f, "overshoot stays near the 16% a 0.5 damping ratio implies");
        checkClose(bouncy.getValue(), 100.0f, 0.75d, "bouncy spring stops inside its value threshold");
        check(bouncySettle > 0L, "bouncy spring takes at least one frame");

        SpringSolver critical = new SpringSolver(new SpringConfig(1500.0f, 1.0f).withFinalPosition(100.0f));
        Peak criticalPeak = new Peak();
        long criticalSettle = settle(critical, 8L, 3000L, criticalPeak);
        check(criticalPeak.max <= 100.0f + 1e-3f, "critically damped spring never overshoots");
        check(criticalPeak.min >= -1e-3f, "critically damped spring is monotonic from zero");

        SpringSolver overdamped = new SpringSolver(new SpringConfig(1500.0f, 1.5f).withFinalPosition(100.0f));
        Peak overdampedPeak = new Peak();
        long overdampedSettle = settle(overdamped, 8L, 3000L, overdampedPeak);
        check(overdampedPeak.max <= 100.0f + 1e-3f, "overdamped spring never overshoots");
        check(overdampedSettle > criticalSettle, "overdamped spring settles slower than critical");

        SpringSolver fast = new SpringSolver(SpringConfig.DEFAULT.withFinalPosition(100.0f));
        SpringSolver slow = new SpringSolver(SpringConfig.DEFAULT.withFinalPosition(100.0f));
        for (int i = 0; i < 50; i++) fast.advance(4L);
        for (int i = 0; i < 12; i++) slow.advance(16L);
        slow.advance(8L);
        checkClose(fast.getValue(), slow.getValue(), 0.2d, "4ms and 16ms frames agree at 200ms");

        SpringSolver retargeted = new SpringSolver(SpringConfig.DEFAULT.withFinalPosition(100.0f));
        for (int i = 0; i < 3; i++) retargeted.advance(8L);
        float midway = retargeted.getValue();
        check(midway > 0.0f && midway < 100.0f, "spring is mid-flight before retargeting");
        retargeted.setFinalPosition(-100.0f);
        settle(retargeted, 8L, 3000L, null);
        checkClose(retargeted.getValue(), -100.0f, 0.75d, "retargeted spring lands on the new position");

        SpringSolver alphaSpring = new SpringSolver(
                SpringConfig.DEFAULT.withFinalPosition(1.0f), SpringSolver.MIN_VISIBLE_CHANGE_ALPHA);
        settle(alphaSpring, 8L, 3000L, null);
        check(Math.abs(alphaSpring.getValue() - 1.0f) < 0.003d, "alpha spring stops within a 1/256 step");

        SpringSolver still = new SpringSolver(SpringConfig.DEFAULT.withFinalPosition(50.0f));
        float before = still.getValue();
        check(still.advance(0L), "zero-length frame keeps the spring running");
        check(still.getValue() == before, "zero-length frame does not move the spring");
        check(still.advance(-8L), "negative frame keeps the spring running");
        check(still.getValue() == before, "negative frame does not move the spring");

        SpringSolver unset = new SpringSolver(SpringConfig.DEFAULT);
        check(!unset.hasFinalPosition(), "unset spring reports no final position");
        checkThrows(() -> unset.advance(16L), "advancing a spring with no final position");

        SpringSolver flicked = new SpringSolver(
                SpringConfig.DEFAULT.withStartVelocity(-2000.0f).withFinalPosition(0.0f));
        flicked.setValue(300.0f);
        flicked.advance(8L);
        check(flicked.getValue() < 300.0f, "a negative start velocity moves the spring immediately");

        System.out.println("SpringSolverTest: PASS");
    }
}
