import com.discoveryscreen.overlay.EvieProtocol;

public final class EvieProtocolTest {
    private EvieProtocolTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("EvieProtocolTest: " + msg);
    }

    static void eq(float a, float b, float eps, String msg) {
        if (Math.abs(a - b) > eps) {
            throw new AssertionError("EvieProtocolTest: " + msg + " expected=" + b + " got=" + a);
        }
    }

    public static void run() {
        check(EvieProtocol.MSG_ATTACH_WINDOW == 0, "attach=0");
        check(EvieProtocol.MSG_UPDATE_SCROLL == 2, "update=2");
        check(EvieProtocol.MSG_SERVICE_STATE_CHANGED == 8, "state=8");
        check(EvieProtocol.MSG_LIGHT_STATUS_BAR == 10, "light status=10");
        check(EvieProtocol.MSG_SETTINGS_INTENT == 14, "settings=14");
        check(EvieProtocol.MSG_LIGHT_NAV_BAR == 15, "light nav=15");

        EvieProtocol.AttachFlags none = EvieProtocol.parseAttachFlags(0);
        check(!none.isRtl && !none.isLightStatusBar && !none.isStatusBarHidden
                && !none.isLightNavBar && !none.isTranslucentNavBar, "flags 0");
        EvieProtocol.AttachFlags all = EvieProtocol.parseAttachFlags(31);
        check(all.isRtl && all.isLightStatusBar && all.isStatusBarHidden
                && all.isLightNavBar && all.isTranslucentNavBar, "flags 31");
        EvieProtocol.AttachFlags rtlOnly = EvieProtocol.parseAttachFlags(1);
        check(rtlOnly.isRtl && !rtlOnly.isLightStatusBar, "flags rtl");
        EvieProtocol.AttachFlags hi = EvieProtocol.parseAttachFlags(1 | 0x100);
        check(hi.isRtl && !hi.isLightNavBar, "unknown bits ignored");

        eq(EvieProtocol.computeTranslationX(0f, 1080, false), -1080f, 0.001f, "ltr closed");
        eq(EvieProtocol.computeTranslationX(1f, 1080, false), 0f, 0.001f, "ltr open");
        eq(EvieProtocol.computeTranslationX(0.5f, 1080, false), -540f, 0.001f, "ltr half");
        eq(EvieProtocol.computeTranslationX(0f, 1080, true), 1080f, 0.001f, "rtl closed");
        eq(EvieProtocol.computeTranslationX(1f, 1080, true), 0f, 0.001f, "rtl open");
        eq(EvieProtocol.computeTranslationX(0.5f, 1080, true), 540f, 0.001f, "rtl half");
        eq(EvieProtocol.offscreenTranslationX(1080, false), -1080f, 0.001f, "ltr offscreen");
        eq(EvieProtocol.offscreenTranslationX(1080, true), 1080f, 0.001f, "rtl offscreen");

        float[] ps = {0.13f, 0.5f, 0.87f};
        for (float p : ps) {
            float txL = EvieProtocol.computeTranslationX(p, 1080, false);
            eq(EvieProtocol.progressFromTranslationX(txL, 1080, false), p, 0.001f, "ltr roundtrip " + p);
            float txR = EvieProtocol.computeTranslationX(p, 1080, true);
            eq(EvieProtocol.progressFromTranslationX(txR, 1080, true), p, 0.001f, "rtl roundtrip " + p);
        }
        eq(EvieProtocol.clampProgress(-0.5f), 0f, 0f, "clamp low");
        eq(EvieProtocol.clampProgress(1.7f), 1f, 0f, "clamp high");
        eq(EvieProtocol.clampProgress(Float.NaN), 0f, 0f, "clamp nan");
        check(EvieProtocol.shouldOpen(0.1f, 301f), "fling opens");
        check(!EvieProtocol.shouldOpen(0.1f, 300f), "boundary velocity not open");
        check(EvieProtocol.shouldOpen(0.36f, 0f), "drag past threshold opens");
        check(!EvieProtocol.shouldOpen(0.35f, 0f), "threshold boundary closed");
        check(!EvieProtocol.shouldOpen(0.9f, -301f), "hard reverse fling closes");
        check(EvieProtocol.shouldOpen(0.9f, -299f), "mild reverse still opens when far");

        check(EvieProtocol.quantizeBlur(0f) == 0, "blur 0");
        check(EvieProtocol.quantizeBlur(0.005f) == 0, "blur tiny 0");
        check(EvieProtocol.quantizeBlur(1f) == EvieProtocol.MAX_BLUR_RADIUS, "blur open max");
        check(EvieProtocol.quantizeBlur(0.96f) == EvieProtocol.MAX_BLUR_RADIUS, "blur snap max");
        int b = EvieProtocol.quantizeBlur(0.2f);
        check(b % 4 == 0, "blur quantized, got " + b);
        check(EvieProtocol.quantizeBlur(0.1f) > 0, "blur early onset");
        int prev = -1;
        for (int i = 0; i <= 100; i++) {
            int cur = EvieProtocol.quantizeBlur(i / 100f);
            check(cur >= prev, "blur monotonic @" + i);
            prev = cur;
        }

        eq(EvieProtocol.computeDim(0.5f, 0), 0f, 0f, "dim 0 blur");
        float d = EvieProtocol.computeDim(0.5f, 60);
        check(d >= 0.01f && d <= 0.12f, "dim range, got " + d);
        eq(EvieProtocol.computeDim(1f, EvieProtocol.MAX_BLUR_RADIUS), 0.12f, 0.001f, "dim max");

        System.out.println("EvieProtocolTest: PASS");
    }
}
