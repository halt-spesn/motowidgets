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
        return WidgetListCodec.parse(raw);
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
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("widget_height_" + widgetId)
                .remove("widget_cell_" + widgetId).apply();
    }

    public static synchronized void moveWidget(Context context, int fromPos, int toPos) {
        List<Integer> list = getWidgetIds(context);
        if (fromPos >= 0 && fromPos < list.size() && toPos >= 0 && toPos < list.size()) {
            int id = list.remove(fromPos);
            list.add(toPos, id);
            saveList(context, list);

            // The rendered order comes from the stored grid placement, so swap
            // the two widgets' placements as well. Any overlap introduced by
            // differing spans is resolved on the next reload.
            int idFrom = list.get(fromPos);
            int idTo = list.get(toPos);
            int[] pFrom = getPlacement(context, idFrom);
            int[] pTo = getPlacement(context, idTo);
            if (pFrom != null && pTo != null) {
                setPlacement(context, idFrom, pTo[0], pTo[1], pTo[2]);
                setPlacement(context, idTo, pFrom[0], pFrom[1], pFrom[2]);
            }
        }
    }

    public static int getWidgetHeightDp(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt("widget_height_" + widgetId, 0);
    }

    public static int cycleWidgetHeight(Context context, int widgetId) {
        int next = WidgetHeightPolicy.nextHeight(getWidgetHeightDp(context, widgetId));
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt("widget_height_" + widgetId, next).apply();
        return next;
    }

    public static synchronized List<Integer> pruneStaleWidgets(Context context,
            java.util.Set<Integer> aliveIds) {
        List<Integer> stored = getWidgetIds(context);
        List<Integer> pruned = WidgetListCodec.pruneStale(stored, aliveIds);
        if (pruned.size() != stored.size() || !pruned.containsAll(stored)) {
            saveList(context, pruned);
        }
        return pruned;
    }

    /**
     * Prune only IDs observed by the caller's provider query. IDs added while
     * that query was running have not been checked yet and must survive it.
     */
    public static synchronized List<Integer> pruneStaleWidgets(Context context,
            List<Integer> queriedIds, java.util.Set<Integer> aliveIds) {
        java.util.Set<Integer> queried = new java.util.HashSet<>(queriedIds);
        List<Integer> current = getWidgetIds(context);
        List<Integer> pruned = new ArrayList<>(current.size());
        for (Integer id : current) {
            if (id != null && (!queried.contains(id) || aliveIds.contains(id))) {
                pruned.add(id);
            }
        }
        if (pruned.size() != current.size()) saveList(context, pruned);
        List<Integer> checked = new ArrayList<>(queriedIds.size());
        for (Integer id : current) {
            if (id != null && queried.contains(id) && aliveIds.contains(id)) checked.add(id);
        }
        return checked;
    }

    public static synchronized void setWidgetHeightDp(Context context, int widgetId, int heightDp) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt("widget_height_" + widgetId, Math.max(0, heightDp)).apply();
    }

    public static synchronized void setWidgetIds(Context context, List<Integer> ids) {
        saveList(context, new ArrayList<>(ids));
    }

    public static boolean hasPlacement(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.contains("widget_cell_" + widgetId);
    }

    public static int[] getPlacement(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString("widget_cell_" + widgetId, null);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static synchronized void setPlacement(Context context, int widgetId,
            int row, int cellX, int spanX) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("widget_cell_" + widgetId,
                Math.max(0, row) + "," + Math.max(0, cellX) + "," + Math.max(1, spanX)).apply();
    }

    /**
     * Free placement keeps row indices stable, so deleting a widget can leave an
     * empty row behind. If the deleted widget was the last one in its row, pull
     * every row above it down by one so the shelf closes up (other gaps the user
     * created by moving widgets are left alone).
     */
    public static synchronized void closeRowGap(Context context, int removedRow) {
        removedRow = Math.max(0, removedRow);
        List<Integer> ids = getWidgetIds(context);
        for (int id : ids) {
            int[] p = getPlacement(context, id);
            if (p != null && Math.max(0, p[0]) == removedRow) {
                return;
            }
        }
        for (int id : ids) {
            int[] p = getPlacement(context, id);
            if (p != null && p[0] > removedRow) {
                setPlacement(context, id, p[0] - 1, p[1], p[2]);
            }
        }
    }

    private static void saveList(Context context, List<Integer> list) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_WIDGET_IDS, WidgetListCodec.serialize(list)).apply();
    }
}
