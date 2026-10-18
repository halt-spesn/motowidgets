package com.discoveryscreen.ui;

import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

final class EdgeToEdge {
    private EdgeToEdge() {
    }

    /** Capture base padding so repeated inset dispatches do not accumulate padding. */
    static void padFor(View view, int insetTypes) {
        int baseLeft = view.getPaddingLeft();
        int baseTop = view.getPaddingTop();
        int baseRight = view.getPaddingRight();
        int baseBottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            Insets insets = windowInsets.getInsets(insetTypes);
            target.setPadding(baseLeft + insets.left, baseTop + insets.top,
                    baseRight + insets.right, baseBottom + insets.bottom);
            return windowInsets;
        });
    }
}
