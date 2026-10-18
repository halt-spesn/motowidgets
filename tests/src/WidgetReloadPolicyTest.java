import com.discoveryscreen.widget.WidgetReloadPolicy;
import java.util.Arrays;
import java.util.Collections;

public final class WidgetReloadPolicyTest {
    private WidgetReloadPolicyTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("WidgetReloadPolicyTest: " + msg);
    }

    public static void run() {
        check(!WidgetReloadPolicy.idsEqual(Arrays.asList(1), null), "null rhs");
        check(WidgetReloadPolicy.idsEqual(Arrays.asList(1, 2), Arrays.asList(1, 2)), "equal");
        check(!WidgetReloadPolicy.idsEqual(Arrays.asList(1, 2), Arrays.asList(2, 1)), "order matters");
        check(!WidgetReloadPolicy.idsEqual(Arrays.asList(1), Arrays.asList(1, 2)), "size");
        check(WidgetReloadPolicy.idsEqual(Collections.emptyList(), Collections.emptyList()), "empty");

        check(WidgetReloadPolicy.shouldReload(false, true, false, 1000L, -1L), "first runs");
        check(WidgetReloadPolicy.shouldReload(true, true, false, 1000L, 900L), "force runs");
        check(WidgetReloadPolicy.shouldReload(false, false, false, 1000L, 900L), "ids change runs");
        check(WidgetReloadPolicy.shouldReload(false, true, true, 1000L, 900L), "edit change runs");
        check(!WidgetReloadPolicy.shouldReload(false, true, false, 1000L, 900L), "debounce skips");
        check(!WidgetReloadPolicy.shouldReload(false, true, false, 1000L, 1000L), "same ms skips");
        check(WidgetReloadPolicy.shouldReload(false, true, false,
                900L + WidgetReloadPolicy.DEBOUNCE_MS, 900L), "post-window runs");
        check(WidgetReloadPolicy.shouldReload(false, true, false,
                900L + WidgetReloadPolicy.DEBOUNCE_MS + 1, 900L), "post-window+1 runs");

        System.out.println("WidgetReloadPolicyTest: PASS");
    }
}
