package com.discoveryscreen.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.appwidget.AppWidgetHostView;
import android.util.DisplayMetrics;
import android.util.SizeF;
import java.util.ArrayList;

public final class WidgetSizing {
    private WidgetSizing() {}

    public static int shelfWidthDp(Context context, int horizontalPaddingDp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int w = (int) (dm.widthPixels / dm.density) - horizontalPaddingDp;
        return Math.max(280, w);
    }

    public static Bundle buildWidgetOptions(Context context, AppWidgetProviderInfo info,
                                            int widthDp, int heightDp) {
        int effectiveHeight = effectiveHeightDp(context, info, heightDp);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, effectiveHeight);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, effectiveHeight);

        return options;
    }

    /** Provider dimensions returned by AppWidgetManager are pixels; options use dp. */
    public static int effectiveHeightDp(Context context, AppWidgetProviderInfo info, int heightDp) {
        if (heightDp > 0) return heightDp;
        if (info == null || info.minHeight <= 0) return 150;
        return Math.max(1, (int) Math.ceil(info.minHeight
                / context.getResources().getDisplayMetrics().density));
    }

    /** Initial width for a new widget; a stored or user-resized span takes precedence. */
    public static int defaultSpanX(Context context, AppWidgetProviderInfo info) {
        int columns = WidgetGridLayout.GRID_COLUMNS;
        if (info == null) return columns;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
            return Math.max(1, Math.min(info.targetCellWidth, columns));
        }
        int width = context.getResources().getDisplayMetrics().widthPixels
                - Math.round(32f * context.getResources().getDisplayMetrics().density);
        int gap = WidgetSpacing.borderSpacingPx(context);
        int cell = Math.max(1, (width - (columns - 1) * gap) / columns);
        int span = (int) Math.ceil((Math.max(1, info.minWidth) + gap) / (float) (cell + gap));
        return Math.max(1, Math.min(span, columns));
    }

    /** Notify the provider using the actual host bounds, with one current size on API 31+. */
    public static void updateHostViewSize(Context context, AppWidgetHostView hostView,
                                          AppWidgetProviderInfo info,
                                          int widthDp, int heightDp) {
        if (hostView == null) return;
        int effectiveHeight = effectiveHeightDp(context, info, heightDp);
        Bundle options = buildWidgetOptions(context, info, widthDp, effectiveHeight);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ArrayList<SizeF> sizes = new ArrayList<>(1);
            sizes.add(new SizeF((float) widthDp, (float) effectiveHeight));
            hostView.updateAppWidgetSize(options, sizes);
        } else {
            hostView.updateAppWidgetSize(options, widthDp, effectiveHeight,
                    widthDp, effectiveHeight);
        }
    }
}
