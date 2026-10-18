package com.discoveryscreen.ui;

import android.content.Context;
import android.os.Build;
import android.view.WindowManager;

public final class BlurSupport {
    private BlurSupport() {}

    public static boolean isCrossWindowBlurEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return windowManager != null && windowManager.isCrossWindowBlurEnabled();
    }
}
