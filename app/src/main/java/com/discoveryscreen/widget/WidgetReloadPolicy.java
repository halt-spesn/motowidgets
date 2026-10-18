package com.discoveryscreen.widget;

import java.util.List;

public final class WidgetReloadPolicy {
    private WidgetReloadPolicy() {}

    public static final long DEBOUNCE_MS = 500L;

    public static boolean shouldReload(boolean force, boolean idsEqual,
                                       boolean editModeChanged,
                                       long nowMs, long lastReloadMs) {
        if (force) return true;
        if (!idsEqual) return true;
        if (editModeChanged) return true;
        if (lastReloadMs < 0) return true;
        return (nowMs - lastReloadMs) >= DEBOUNCE_MS;
    }

    public static boolean idsEqual(List<Integer> a, List<Integer> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Integer x = a.get(i);
            Integer y = b.get(i);
            if (x == null ? y != null : !x.equals(y)) return false;
        }
        return true;
    }
}
