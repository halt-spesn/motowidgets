package com.discoveryscreen.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

public class MonetColors {
    public static int getAccentColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent1_300);
            } catch (Exception ignored) {}
        }
        return Color.parseColor("#0A84FF");
    }

    public static int getSecondaryAccentColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent2_300);
            } catch (Exception ignored) {}
        }
        return Color.parseColor("#30D158");
    }

    public static int getNeutralTextColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_neutral1_100);
            } catch (Exception ignored) {}
        }
        return Color.parseColor("#EBEBF5");
    }
}
