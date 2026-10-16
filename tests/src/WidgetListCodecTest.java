import com.discoveryscreen.widget.WidgetListCodec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WidgetListCodecTest {
    private WidgetListCodecTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("WidgetListCodecTest: " + msg);
    }

    public static void run() {
        check(WidgetListCodec.parse(null).isEmpty(), "null -> empty");
        check(WidgetListCodec.parse("").isEmpty(), "empty -> empty");

        List<Integer> ids = WidgetListCodec.parse("12,34, 56");
        check(ids.equals(Arrays.asList(12, 34, 56)), "parse basic: " + ids);

        List<Integer> mixed = WidgetListCodec.parse("7,xx,,9,  ,abc,10");
        check(mixed.equals(Arrays.asList(7, 9, 10)), "parse corrupt: " + mixed);

        check(WidgetListCodec.serialize(null).equals(""), "serialize null");
        check(WidgetListCodec.serialize(Arrays.asList(1, 2, 3)).equals("1,2,3"), "serialize");

        List<Integer> orig = Arrays.asList(101, 202, 303);

        List<Integer> stored = Arrays.asList(1, 2, 3, 2, 4);
        Set<Integer> alive = new HashSet<>(Arrays.asList(2, 4, 5));
        List<Integer> pruned = WidgetListCodec.pruneStale(stored, alive);
        check(pruned.equals(Arrays.asList(2, 4)), "prune: " + pruned);

        check(WidgetListCodec.pruneStale(null, alive).isEmpty(), "prune null stored");
        check(WidgetListCodec.pruneStale(stored, null).isEmpty(), "prune null alive");

        System.out.println("WidgetListCodecTest: PASS");
    }
}
