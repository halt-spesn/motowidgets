package com.discoveryscreen.widget;

import java.util.ArrayList;
import java.util.List;

public final class WidgetListCodec {
    private WidgetListCodec() {}

    public static List<Integer> parse(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] parts = raw.split(",");
        for (String part : parts) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static String serialize(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    public static List<Integer> pruneStale(List<Integer> stored, java.util.Set<Integer> alive) {
        List<Integer> out = new ArrayList<>();
        if (stored == null || alive == null) return out;
        for (Integer id : stored) {
            if (id == null) continue;
            if (alive.contains(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
