package com.discoveryscreen.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;

public final class WidgetSpacing {
    private WidgetSpacing() {}

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static int dimenPx(Context context, String name, float fallbackDp) {
        Resources res = context.getResources();
        try {
            int id = res.getIdentifier(name, "dimen", context.getPackageName());
            if (id != 0) {
                return res.getDimensionPixelSize(id);
            }
        } catch (Exception ignored) {
        }
        return dpToPx(context, fallbackDp);
    }

    public static int borderSpacingPx(Context context) {
        return dimenPx(context, "dynamic_grid_cell_border_spacing", 16f);
    }

    public static int edgeMarginPx(Context context) {
        return dimenPx(context, "dynamic_grid_edge_margin", 11f);
    }

    public static int cellPaddingXPx(Context context) {
        return dimenPx(context, "dynamic_grid_cell_padding_x", 8f);
    }

    public static int pendingMinPaddingPx(Context context) {
        return dimenPx(context, "pending_widget_min_padding", 8f);
    }

    public static int cellVerticalPaddingPx(Context context) {
        return dimenPx(context, "widget_cell_vertical_padding", 8f);
    }

    public static void widgetPaddingForBorder(Context context, int borderPx, Rect out) {
        float density = context.getResources().getDisplayMetrics().density;
        int threshold = Math.round(6f * density);
        if (borderPx < threshold) {
            int pad = threshold - borderPx;
            out.set(pad, pad, pad, pad);
        } else {
            out.setEmpty();
        }
    }
}
