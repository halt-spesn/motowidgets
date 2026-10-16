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
        int effectiveHeight = heightDp > 0 ? heightDp
                : (info != null && info.minHeight > 0 ? info.minHeight : 150);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, effectiveHeight);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, effectiveHeight);

        return options;
    }

    /** Notify the provider using the actual host bounds, with one current size on API 31+. */
    public static void updateHostViewSize(Context context, AppWidgetHostView hostView,
                                          AppWidgetProviderInfo info,
                                          int widthDp, int heightDp) {
        if (hostView == null) return;
        int effectiveHeight = heightDp > 0 ? heightDp
                : (info != null && info.minHeight > 0 ? info.minHeight : 150);
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
