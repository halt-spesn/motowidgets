package com.discoveryscreen.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Log;

public class WidgetHostManager {
    private static final String TAG = "WidgetHostManager";
    public static final int APPWIDGET_HOST_ID = 2048;
    private static AppWidgetHost sHost;

    private static class ShelfAppWidgetHost extends AppWidgetHost {
        ShelfAppWidgetHost(Context context, int hostId) {
            super(context, hostId);
        }

        @Override
        protected AppWidgetHostView onCreateView(Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
            return new RoundedAppWidgetHostView(context);
        }
    }

    public static synchronized AppWidgetHost getHost(Context context) {
        if (sHost == null) {
            sHost = new ShelfAppWidgetHost(context.getApplicationContext(), APPWIDGET_HOST_ID);
        }
        return sHost;
    }

    public static synchronized void ensureListening(Context context) {
        try {
            getHost(context).startListening();
            Log.d(TAG, "AppWidgetHost listening started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting AppWidgetHost listening", e);
        }
    }
}
