package com.discoveryscreen.widget;

import java.util.List;

public final class WidgetSizeTable {
    private WidgetSizeTable() {}

    public static int[] minMaxSizes(List<float[]> sizesDp) {
        if (sizesDp == null || sizesDp.isEmpty()) {
            return new int[]{0, 0, 0, 0};
        }
        float minW = Float.MAX_VALUE;
        float minH = Float.MAX_VALUE;
        float maxW = Float.MIN_VALUE;
        float maxH = Float.MIN_VALUE;
        boolean any = false;
        for (float[] s : sizesDp) {
            if (s == null || s.length < 2) continue;
            float w = s[0];
            float h = s[1];
            if (Float.isNaN(w) || Float.isNaN(h)) continue;
            any = true;
            if (w < minW) minW = w;
            if (h < minH) minH = h;
            if (w > maxW) maxW = w;
            if (h > maxH) maxH = h;
        }
        if (!any) return new int[]{0, 0, 0, 0};
        return new int[]{(int) minW, (int) minH, (int) maxW, (int) maxH};
    }

    public static float[] defaultSize(int widthDp, int fallbackHeightDp) {
        int w = Math.max(280, widthDp);
        int h = fallbackHeightDp > 0 ? fallbackHeightDp : 150;
        return new float[]{(float) w, (float) h};
    }
}
