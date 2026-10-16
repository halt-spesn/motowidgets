import com.discoveryscreen.widget.StagedQueue;
import com.discoveryscreen.widget.WidgetInflationPolicy;
import java.util.ArrayList;
import java.util.List;

public final class StagedQueueTest {
    private StagedQueueTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("StagedQueueTest: " + msg);
    }

    public static void run() {
        StagedQueue q = new StagedQueue();
        List<String> log = new ArrayList<>();
        Runnable a = () -> log.add("a");
        Runnable b = () -> log.add("b");
        Runnable c = () -> log.add("c");
        StagedQueue.CostGate never = cost -> false;

        check(q.isEmpty() && q.takeReady(0, never) == null, "empty drains null");

        q.offer(1, -1L, a);
        q.offer(1, 25L, b);
        q.offer(1, 293L, c);
        check(q.size() == 3, "size after offers");
        q.takeReady(1, never).run();
        q.takeReady(1, never).run();
        q.takeReady(1, never).run();
        check(log.toString().equals("[a, b, c]"), "fifo order, was " + log);
        check(q.isEmpty(), "drained");

        q.offer(1, -1L, a);
        q.offer(1, 293L, b);
        StagedQueue.CostGate slide = cost -> WidgetInflationPolicy.shouldDefer(true, cost);
        check(q.takeReady(1, slide) == null, "unknown head yields to slide");
        check(q.size() == 2, "yield keeps queue");
        StagedQueue.CostGate slideOrNot = cost -> false;
        check(q.takeReady(1, slideOrNot) == a, "fast/unknown head runs when allowed");
        q.clear();

        q.offer(2, 25L, a);
        q.offer(2, 293L, b);
        check(q.takeReady(2, slide) == a, "fast head runs during slide");
        check(q.takeReady(2, slide) == null, "slow head waits for settle");
        q.clear();

        q.offer(5, -1L, a);
        q.offer(6, 25L, b);
        check(q.takeReady(6, never) == b, "stale head discarded, fresh runs");
        check(q.isEmpty(), "stale discarded fully");
        q.offer(6, 25L, a);
        q.offer(6, 293L, b);
        check(q.takeReady(7, never) == null, "all-stale drains to null");
        check(q.isEmpty(), "all-stale fully discarded");

        q.offer(7, 25L, a);
        q.offer(8, -1L, b);
        check(q.takeReady(8, slide) == null, "stale discarded, unknown head yields");
        check(q.takeReady(8, never) == b, "fresh head runs once allowed");

        q.clear();
        check(q.isEmpty() && q.takeReady(8, never) == null, "clear drops everything");

        System.out.println("StagedQueueTest: PASS");
    }
}
