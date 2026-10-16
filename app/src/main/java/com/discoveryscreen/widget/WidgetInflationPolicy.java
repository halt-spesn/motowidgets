package com.discoveryscreen.widget;

public final class WidgetInflationPolicy {
    private WidgetInflationPolicy() {}

    public static final long SLOW_THRESHOLD_MS = 100L;

    public static boolean isSlow(long lastCostMs) {
        return lastCostMs >= 0 && lastCostMs >= SLOW_THRESHOLD_MS;
    }

    public static boolean readyDuringGesture(long lastCostMs) {
        return lastCostMs >= 0 && lastCostMs < SLOW_THRESHOLD_MS;
    }

    public static boolean cacheValid(int cachedHeightDp, boolean cachedEditMode, int cachedPos,
                                     int currentHeightDp, boolean currentEditMode, int currentPos) {
        if (cachedHeightDp != currentHeightDp || cachedEditMode != currentEditMode) {
            return false;
        }
        // In edit mode the card body embeds the up/down affordances, whose
        // enabled state depends on its list position.
        return !currentEditMode || cachedPos == currentPos;
    }

    public static boolean shouldDefer(boolean gestureBlocking, long lastCostMs) {
        return gestureBlocking && !readyDuringGesture(lastCostMs);
    }
}
