package com.discoveryscreen.widget;

public final class WidgetHeightPolicy {
    private WidgetHeightPolicy() {}

    public static final int AUTO = 0;
    public static final int SMALL = 140;
    public static final int MEDIUM = 200;
    public static final int LARGE = 280;
    public static final int XLARGE = 380;
    public static final int TALL = 480;

    public static int nextHeight(int currentDp) {
        if (currentDp <= AUTO) return SMALL;
        if (currentDp <= SMALL) return MEDIUM;
        if (currentDp <= MEDIUM) return LARGE;
        if (currentDp <= LARGE) return XLARGE;
        if (currentDp <= XLARGE) return TALL;
        return AUTO;
    }
}
