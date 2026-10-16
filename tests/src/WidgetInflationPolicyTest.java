import com.discoveryscreen.widget.WidgetInflationPolicy;

public final class WidgetInflationPolicyTest {
    private WidgetInflationPolicyTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("WidgetInflationPolicyTest: " + msg);
    }

    public static void run() {
        check(!WidgetInflationPolicy.isSlow(-1L), "unknown not slow");
        check(!WidgetInflationPolicy.isSlow(5L), "5ms fast");
        check(!WidgetInflationPolicy.isSlow(99L), "99ms fast");
        check(WidgetInflationPolicy.isSlow(100L), "100ms slow");
        check(WidgetInflationPolicy.isSlow(293L), "293ms slow");

        check(WidgetInflationPolicy.cacheValid(0, false, 0, 0, false, 0), "same valid");
        check(WidgetInflationPolicy.cacheValid(200, true, 3, 200, true, 3), "same valid 2");
        check(!WidgetInflationPolicy.cacheValid(0, false, 0, 200, false, 0), "height change");
        check(!WidgetInflationPolicy.cacheValid(0, false, 0, 0, true, 0), "edit change");
        check(!WidgetInflationPolicy.cacheValid(200, true, 0, 0, false, 0), "both change");
        check(WidgetInflationPolicy.cacheValid(200, false, 0, 200, false, 5),
                "position ignored outside edit mode");
        check(!WidgetInflationPolicy.cacheValid(200, true, 0, 200, true, 5),
                "position change invalidates in edit mode");

        check(!WidgetInflationPolicy.shouldDefer(false, 293L), "warm never defers");
        check(!WidgetInflationPolicy.shouldDefer(false, -1L), "warm never defers (unknown)");
        check(!WidgetInflationPolicy.shouldDefer(true, 25L), "fast never defers");
        check(WidgetInflationPolicy.shouldDefer(true, 293L), "slow swipe defers");
        check(WidgetInflationPolicy.shouldDefer(true, -1L), "unknown swipe defers");

        System.out.println("WidgetInflationPolicyTest: PASS");
    }
}
