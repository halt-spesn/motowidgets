package com.discoveryscreen.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

public class RoundedAppWidgetHostView extends AppWidgetHostView {
    private float mCornerRadius;

    public RoundedAppWidgetHostView(Context context) {
        super(context);
        mCornerRadius = computeCornerRadius(context);
        setClipToOutline(true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        enforceRoundedCorners();
    }

    private void enforceRoundedCorners() {
        if (mCornerRadius <= 0) {
            mCornerRadius = computeCornerRadius(getContext());
        }
        if (mCornerRadius <= 0) return;

        // In AppWidgetHostView, getChildAt(0) is the root container of the RemoteViews layout
        View widgetRoot = getChildCount() > 0 ? getChildAt(0) : null;
        if (widgetRoot != null && widgetRoot.getWidth() > 0 && widgetRoot.getHeight() > 0) {
            final int rLeft = widgetRoot.getLeft();
            final int rTop = widgetRoot.getTop();
            final int rRight = widgetRoot.getRight();
            final int rBottom = widgetRoot.getBottom();

            // Clip the root container of the widget with rounded corners
            widgetRoot.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerRadius);
                }
            });
            widgetRoot.setClipToOutline(true);

            // Also clip the host view to the exact bounds of the widget root
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(rLeft, rTop, rRight, rBottom, mCornerRadius);
                }
            });
            setClipToOutline(true);
            invalidateOutline();
            return;
        }

        // Fallback: clip whole host view
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerRadius);
            }
        });
        setClipToOutline(true);
        invalidateOutline();
    }

    private static float computeCornerRadius(Context context) {
        float radius = 16 * context.getResources().getDisplayMetrics().density;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                int resId = android.R.dimen.system_app_widget_background_radius;
                float sysRadius = context.getResources().getDimension(resId);
                if (sysRadius > 0) {
                    radius = sysRadius;
                }
            } catch (Exception ignored) {}
        }
        return radius;
    }
}
