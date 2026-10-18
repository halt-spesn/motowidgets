import com.discoveryscreen.widget.WidgetSizeTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WidgetSizeTableTest {
    private WidgetSizeTableTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("WidgetSizeTableTest: " + msg);
    }

    public static void run() {
        check(Arrays.equals(WidgetSizeTable.minMaxSizes(null), new int[]{0, 0, 0, 0}), "null");
        List<float[]> two = new ArrayList<>();
        two.add(new float[]{320f, 150f});
        two.add(new float[]{320f, 110f});
        int[] mm = WidgetSizeTable.minMaxSizes(two);
        check(mm[0] == 320 && mm[1] == 110 && mm[2] == 320 && mm[3] == 150,
                "envelope: " + Arrays.toString(mm));

        List<float[]> messy = new ArrayList<>();
        messy.add(null);
        messy.add(new float[]{Float.NaN, 10f});
        messy.add(new float[]{200f, 100f});
        int[] mm2 = WidgetSizeTable.minMaxSizes(messy);
        check(mm2[0] == 200 && mm2[3] == 100, "messy: " + Arrays.toString(mm2));
        float[] d = WidgetSizeTable.defaultSize(100, 0);
        check(d[0] >= 280 && d[1] == 150, "default clamps: " + Arrays.toString(d));
        float[] d2 = WidgetSizeTable.defaultSize(400, 200);
        check(d2[0] == 400 && d2[1] == 200, "default keeps: " + Arrays.toString(d2));

        System.out.println("WidgetSizeTableTest: PASS");
    }
}
