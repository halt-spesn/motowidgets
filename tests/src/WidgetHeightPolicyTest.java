import com.discoveryscreen.widget.WidgetHeightPolicy;

public final class WidgetHeightPolicyTest {
    private WidgetHeightPolicyTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("WidgetHeightPolicyTest: " + msg);
    }

    public static void run() {
        check(WidgetHeightPolicy.nextHeight(0) == 140, "auto->small");
        check(WidgetHeightPolicy.nextHeight(140) == 200, "small->medium");
        check(WidgetHeightPolicy.nextHeight(200) == 280, "medium->large");
        check(WidgetHeightPolicy.nextHeight(280) == 380, "large->xl");
        check(WidgetHeightPolicy.nextHeight(380) == 480, "xl->tall");
        check(WidgetHeightPolicy.nextHeight(480) == 0, "tall->auto");
        System.out.println("WidgetHeightPolicyTest: PASS");
    }
}
