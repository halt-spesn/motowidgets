package com.discoveryscreen.overlay;

public final class EvieProtocol {
    private EvieProtocol() {}

    public static final int MSG_ATTACH_WINDOW = 0;
    public static final int MSG_START_SCROLL = 1;
    public static final int MSG_UPDATE_SCROLL = 2;
    public static final int MSG_END_SCROLL = 3;
    public static final int MSG_PAUSE = 4;
    public static final int MSG_DETACH_WINDOW = 5;
    public static final int MSG_RESUME = 6;
    public static final int MSG_SERVICE_STATE_CHANGED = 8;
    public static final int MSG_LIGHT_STATUS_BAR = 10;
    public static final int MSG_HIDE = 11;
    public static final int MSG_TURN_OFF = 13;
    public static final int MSG_SETTINGS_INTENT = 14;
    public static final int MSG_LIGHT_NAV_BAR = 15;

    public static final int FLAG_RTL = 1;
    public static final int FLAG_LIGHT_STATUS_BAR = 2;
    public static final int FLAG_STATUS_BAR_HIDDEN = 4;
    public static final int FLAG_LIGHT_NAV_BAR = 8;
    public static final int FLAG_TRANSLUCENT_NAV_BAR = 16;

    public static final float OPEN_VELOCITY_THRESHOLD = 300.0f;
    public static final float OPEN_PROGRESS_THRESHOLD = 0.35f;

    public static final float BLUR_FACTOR_SCALE = 2.2f;
    public static final int MAX_BLUR_RADIUS = 70;
    public static final int BLUR_QUANTUM = 4;
    public static final float MAX_DIM_AMOUNT = 0.12f;
    public static final float MIN_VISIBLE_DIM = 0.01f;

    public static final class AttachFlags {
        public final boolean isRtl;
        public final boolean isLightStatusBar;
        public final boolean isStatusBarHidden;
        public final boolean isLightNavBar;
        public final boolean isTranslucentNavBar;

        public AttachFlags(boolean rtl, boolean lightStatus, boolean statusHidden,
                           boolean lightNav, boolean translucentNav) {
            this.isRtl = rtl;
            this.isLightStatusBar = lightStatus;
            this.isStatusBarHidden = statusHidden;
            this.isLightNavBar = lightNav;
            this.isTranslucentNavBar = translucentNav;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AttachFlags)) return false;
            AttachFlags other = (AttachFlags) o;
            return isRtl == other.isRtl
                    && isLightStatusBar == other.isLightStatusBar
                    && isStatusBarHidden == other.isStatusBarHidden
                    && isLightNavBar == other.isLightNavBar
                    && isTranslucentNavBar == other.isTranslucentNavBar;
        }

        @Override
        public String toString() {
            return "AttachFlags{rtl=" + isRtl + ",lightStatus=" + isLightStatusBar
                    + ",statusHidden=" + isStatusBarHidden + ",lightNav=" + isLightNavBar
                    + ",translucentNav=" + isTranslucentNavBar + "}";
        }
    }

    public static AttachFlags parseAttachFlags(int arg1) {
        return new AttachFlags(
                (arg1 & FLAG_RTL) != 0,
                (arg1 & FLAG_LIGHT_STATUS_BAR) != 0,
                (arg1 & FLAG_STATUS_BAR_HIDDEN) != 0,
                (arg1 & FLAG_LIGHT_NAV_BAR) != 0,
                (arg1 & FLAG_TRANSLUCENT_NAV_BAR) != 0);
    }

    public static boolean parseLightStatusArg(int arg1) {
        return (arg1 & FLAG_LIGHT_STATUS_BAR) != 0;
    }

    public static boolean parseLightNavArg(int arg1) {
        return (arg1 & FLAG_LIGHT_NAV_BAR) != 0;
    }

    public static float clampProgress(float p) {
        if (Float.isNaN(p)) return 0.0f;
        if (p < 0.0f) return 0.0f;
        if (p > 1.0f) return 1.0f;
        return p;
    }

    public static float computeTranslationX(float progress, int screenWidth, boolean isRtl) {
        float p = clampProgress(progress);
        if (screenWidth <= 0) return 0.0f;
        if (isRtl) {
            return (1.0f - p) * screenWidth;
        }
        return (p - 1.0f) * screenWidth;
    }

    public static float offscreenTranslationX(int screenWidth, boolean isRtl) {
        return computeTranslationX(0.0f, screenWidth, isRtl);
    }

    public static float computeAlpha(float progress) {
        return clampProgress(progress);
    }

    public static float progressFromTranslationX(float tx, int screenWidth, boolean isRtl) {
        if (screenWidth <= 0) return 0.0f;
        float p;
        if (isRtl) {
            p = 1.0f - (tx / (float) screenWidth);
        } else {
            p = 1.0f + (tx / (float) screenWidth);
        }
        return clampProgress(p);
    }

    public static boolean shouldOpen(float progress, float velocity) {
        float p = clampProgress(progress);
        if (velocity > OPEN_VELOCITY_THRESHOLD) return true;
        return velocity > -OPEN_VELOCITY_THRESHOLD && p > OPEN_PROGRESS_THRESHOLD;
    }

    public static int quantizeBlur(float progress) {
        float p = clampProgress(progress);
        if (p >= 0.95f) return MAX_BLUR_RADIUS;
        if (p <= 0.01f) return 0;
        float factor = Math.min(p * BLUR_FACTOR_SCALE, 1.0f);
        int blur = Math.max(0, Math.min(MAX_BLUR_RADIUS, Math.round(factor * MAX_BLUR_RADIUS)));
        return (blur / BLUR_QUANTUM) * BLUR_QUANTUM;
    }

    public static float computeDim(float progress, int quantizedBlur) {
        if (quantizedBlur <= 0) return 0.0f;
        float p = clampProgress(progress);
        float dim = Math.max(MIN_VISIBLE_DIM, p * MAX_DIM_AMOUNT);
        return Math.round(dim * 100.0f) / 100.0f;
    }
}
