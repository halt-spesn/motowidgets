import com.discoveryscreen.widget.SpringConfig;

public final class SpringConfigTest {
    private SpringConfigTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("SpringConfigTest: " + msg);
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
        throw new AssertionError("SpringConfigTest: expected IllegalArgumentException for " + msg);
    }

    public static void run() {
        check(SpringConfig.DEFAULT.stiffness == 1500.0f, "default stiffness is 1500");
        check(SpringConfig.DEFAULT.dampingRatio == 0.5f, "default damping ratio is 0.5");
        check(SpringConfig.SNAP_BACK.stiffness == 200.0f, "snap-back stiffness is 200");
        check(SpringConfig.SNAP_BACK.dampingRatio == 0.75f, "snap-back damping ratio is 0.75");
        check(SpringConfig.DEFAULT.startVelocity == 0.0f, "presets start at rest");
        check(!SpringConfig.DEFAULT.hasFinalPosition(), "presets carry no final position");
        check(Float.isNaN(SpringConfig.UNSET_POSITION), "unset position is NaN");

        check(SpringConfig.DEFAULT.equals(new SpringConfig(1500.0f, 0.5f)), "equal values compare equal");
        check(SpringConfig.DEFAULT.hashCode() == new SpringConfig(1500.0f, 0.5f).hashCode(),
                "equal values share a hash");
        check(!SpringConfig.DEFAULT.equals(SpringConfig.SNAP_BACK), "different presets differ");
        check(!SpringConfig.DEFAULT.equals(null), "config never equals null");

        SpringConfig tuned = SpringConfig.SNAP_BACK.withStartVelocity(-1200.0f).withFinalPosition(480.0f);
        check(tuned.startVelocity == -1200.0f, "start velocity applied");
        check(tuned.finalPosition == 480.0f, "final position applied");
        check(tuned.hasFinalPosition(), "final position reported as set");
        check(tuned.stiffness == 200.0f && tuned.dampingRatio == 0.75f, "spring constants survive copies");
        check(!SpringConfig.SNAP_BACK.hasFinalPosition(), "copies do not mutate the original");

        checkThrows(() -> new SpringConfig(0.0f, 0.5f), "zero stiffness");
        checkThrows(() -> new SpringConfig(-1.0f, 0.5f), "negative stiffness");
        checkThrows(() -> new SpringConfig(1500.0f, -0.1f), "negative damping ratio");
        check(new SpringConfig(1500.0f, 0.0f).dampingRatio == 0.0f, "zero damping is an undamped spring, not an error");

        System.out.println("SpringConfigTest: PASS");
    }
}
