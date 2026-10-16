package com.discoveryscreen.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public abstract class RoundedCornerEnforcement {
    public static View findBackground(View view) {
        List<View> found = findViewsWithId(view, android.R.id.background);
        if (found.size() == 1) {
            return found.get(0);
        }
        if (!(view instanceof ViewGroup)) {
            return view;
        }
        ViewGroup group = (ViewGroup) view;
        return group.getChildCount() > 0 ? findUndefinedBackground(group.getChildAt(0)) : view;
    }

    public static boolean hasAppWidgetOptedOut(View host, View background) {
        return background.getId() == android.R.id.background && background.getClipToOutline();
    }

    public static boolean isRoundedCornerEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void computeRoundedRectangle(View host, View background, Rect out) {
        out.left = 0;
        out.right = background.getWidth();
        out.top = 0;
        out.bottom = background.getHeight();
        View v = background;
        while (v != host) {
            out.offset(v.getLeft(), v.getTop());
            if (!(v.getParent() instanceof View)) {
                break;
            }
            v = (View) v.getParent();
        }
    }

    public static float computeEnforcedRadius(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return 0f;
        }
        Resources res = context.getResources();
        float cap;
        try {
            int capId = res.getIdentifier(
                    "enforced_rounded_corner_max_radius", "dimen", context.getPackageName());
            if (capId != 0) {
                cap = res.getDimension(capId);
            } else {
                cap = 16f * res.getDisplayMetrics().density;
            }
        } catch (Exception e) {
            cap = 16f * res.getDisplayMetrics().density;
        }
        float sys;
        try {
            sys = res.getDimension(android.R.dimen.system_app_widget_background_radius);
        } catch (Exception e) {
            sys = cap;
        }
        if (sys <= 0) {
            return cap;
        }
        return Math.min(cap, sys);
    }

    private static List<View> findViewsWithId(View view, int id) {
        List<View> out = new ArrayList<>();
        accumulateViewsWithId(view, id, out);
        return out;
    }

    private static void accumulateViewsWithId(View view, int id, List<View> out) {
        if (view.getId() == id) {
            out.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                accumulateViewsWithId(group.getChildAt(i), id, out);
            }
        }
    }

    private static boolean isViewVisible(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        return !(view.willNotDraw() && view.getForeground() == null && view.getBackground() == null);
    }

    private static View findUndefinedBackground(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (isViewVisible(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            View fallback = null;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findUndefinedBackground(group.getChildAt(i));
                if (candidate != null && fallback == null) {
                    fallback = candidate;
                }
            }
            return fallback;
        }
        return null;
    }
}
