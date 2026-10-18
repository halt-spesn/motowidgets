import com.discoveryscreen.widget.ReloadGate;

public final class ReloadGateTest {
    private ReloadGateTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("ReloadGateTest: " + msg);
    }

    public static void run() {
        ReloadGate.Gate g = new ReloadGate.Gate();
        check(g.onReloadRequest(false) == ReloadGate.RequestDecision.RUN_NOW, "idle runs");
        check(g.onReloadRequest(true) == ReloadGate.RequestDecision.RUN_NOW, "idle force runs");
        check(g.onSettled(true) == ReloadGate.SettleAction.NOTHING, "nothing pending");

        g.onSwipeStart();
        check(g.isSwipeActive(), "active");
        check(g.onReloadRequest(false) == ReloadGate.RequestDecision.DEFERRED, "swipe defers");
        check(g.hasPending(), "pending recorded");
        check(g.onReloadRequest(false) == ReloadGate.RequestDecision.DEFERRED, "repeat defers");

        check(g.onSettled(false) == ReloadGate.SettleAction.NOTHING, "close drops");
        check(!g.hasPending() && !g.isSwipeActive(), "close clears");

        g.onSwipeStart();
        g.onReloadRequest(false);
        check(g.onSettled(true) == ReloadGate.SettleAction.FLUSH_NORMAL, "open flushes");
        check(!g.hasPending(), "flush consumes");
        g.onSwipeStart();
        g.onReloadRequest(false);
        g.onReloadRequest(true);
        check(g.onSettled(true) == ReloadGate.SettleAction.FLUSH_FORCE, "force preserved");

        g.onSwipeStart();
        check(g.onReloadRequest(true) == ReloadGate.RequestDecision.DEFERRED, "force defers");

        g.reset();
        check(!g.isSwipeActive() && !g.hasPending(), "reset clears");
        check(g.onReloadRequest(false) == ReloadGate.RequestDecision.RUN_NOW, "runs after reset");

        System.out.println("ReloadGateTest: PASS");
    }
}
