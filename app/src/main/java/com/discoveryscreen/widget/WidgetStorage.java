package com.discoveryscreen.widget;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class WidgetStorage {
    private static final String PREF_NAME = "moto_widget_shelf_prefs";
    private static final String KEY_WIDGET_IDS = "saved_widget_ids";

    public static List<Integer> getWidgetIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_WIDGET_IDS, "");
        List<Integer> list = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                try {
                    list.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    public static synchronized void addWidgetId(Context context, int widgetId) {
        List<Integer> list = getWidgetIds(context);
        if (!list.contains(widgetId)) {
            list.add(widgetId);
            saveList(context, list);
        }
    }

    public static synchronized void removeWidgetId(Context context, int widgetId) {
        List<Integer> list = getWidgetIds(context);
        if (list.remove(Integer.valueOf(widgetId))) {
            saveList(context, list);
        }
        // Clean up height pref
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("widget_height_" + widgetId).apply();
    }

    public static synchronized void moveWidget(Context context, int fromPos, int toPos) {
        List<Integer> list = getWidgetIds(context);
        if (fromPos >= 0 && fromPos < list.size() && toPos >= 0 && toPos < list.size()) {
            int id = list.remove(fromPos);
            list.add(toPos, id);
            saveList(context, list);
        }
    }

    public static int getWidgetHeightDp(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt("widget_height_" + widgetId, 0); // 0 means Auto / WRAP_CONTENT
    }

    public static int cycleWidgetHeight(Context context, int widgetId) {
        int current = getWidgetHeightDp(context, widgetId);
        int next;
        if (current <= 0) {
            next = 140; // Small
        } else if (current <= 140) {
            next = 200; // Medium
        } else if (current <= 200) {
            next = 280; // Large
        } else if (current <= 280) {
            next = 380; // Extra Large (e.g. Pinterest feeds)
        } else if (current <= 380) {
            next = 480; // Tall
        } else {
            next = 0;   // Auto
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt("widget_height_" + widgetId, next).apply();
        return next;
    }

    private static void saveList(Context context, List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_WIDGET_IDS, sb.toString()).apply();
    }
}
