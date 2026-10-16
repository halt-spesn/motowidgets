package com.discoveryscreen.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterViewAnimator;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import java.util.concurrent.Executor;

public class RoundedAppWidgetHostView extends AppWidgetHostView {
    private static final ViewOutlineProvider VIEW_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
            outline.setAlpha(0f);
        }
    };

    private final ViewOutlineProvider mCornerRadiusEnforcementOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            if (mEnforcedRectangle.isEmpty() || mEnforcedCornerRadius <= 0f) {
                outline.setEmpty();
            } else {
                outline.setRoundRect(mEnforcedRectangle, mEnforcedCornerRadius);
            }
        }
    };

    private final float mEnforcedCornerRadius;
    private final Rect mEnforcedRectangle = new Rect();
    private final int mTouchSlop;
    private final int[] mHitTestLocation = new int[2];
    private boolean mIsCornerRadiusEnforced;
    private float mDownX;
    private float mDownY;
    private boolean mDownOnScrollableContent;
    private boolean mCanceledSwipe;

    private static final Executor THREAD_POOL_EXECUTOR =
            java.util.concurrent.Executors.newCachedThreadPool();

    public RoundedAppWidgetHostView(Context context) {
        super(context);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                setExecutor(THREAD_POOL_EXECUTOR);
            } catch (Exception ignored) {
            }
        }
        setClipToOutline(true);
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownX = event.getRawX();
            mDownY = event.getRawY();
            mDownOnScrollableContent = findScrollableContentAt(this, mDownX, mDownY);
            mCanceledSwipe = false;
        } else if (mCanceledSwipe) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCanceledSwipe = false;
                mDownOnScrollableContent = false;
            }
            return true;
        } else if (action == MotionEvent.ACTION_MOVE && !mDownOnScrollableContent) {
            float dx = event.getRawX() - mDownX;
            float dy = event.getRawY() - mDownY;
            if (dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop) {
                // End the child's tap sequence as soon as this becomes a swipe.
                // This avoids launching a widget from a drag that the widget
                // itself cannot scroll. Scrollable widget content keeps the
                // complete gesture stream.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                mCanceledSwipe = true;
                return true;
            }
        }

        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mDownOnScrollableContent = false;
        }
        return handled;
    }

    private boolean findScrollableContentAt(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != VISIBLE) return false;
        view.getLocationOnScreen(mHitTestLocation);
        if (rawX < mHitTestLocation[0] || rawX > mHitTestLocation[0] + view.getWidth()
                || rawY < mHitTestLocation[1] || rawY > mHitTestLocation[1] + view.getHeight()) {
            return false;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                if (findScrollableContentAt(group.getChildAt(i), rawX, rawY)) return true;
            }
        }
        return view != this && (view instanceof AbsListView || view instanceof ScrollView
                || view instanceof HorizontalScrollView || view instanceof AdapterViewAnimator
                || view instanceof AdapterView || view.isScrollContainer()
                || view.canScrollVertically(1) || view.canScrollVertically(-1)
                || view.canScrollHorizontally(1) || view.canScrollHorizontally(-1));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (RuntimeException e) {
            resetRoundedCorners();
            return;
        }
        enforceRoundedCorners();
    }

    private void resetRoundedCorners() {
        setOutlineProvider(VIEW_OUTLINE_PROVIDER);
        mIsCornerRadiusEnforced = false;
    }

    private void enforceRoundedCorners() {
        if (mEnforcedCornerRadius <= 0f || !RoundedCornerEnforcement.isRoundedCornerEnabled()) {
            resetRoundedCorners();
            return;
        }
        View background = RoundedCornerEnforcement.findBackground(this);
        if (background == null
                || RoundedCornerEnforcement.hasAppWidgetOptedOut(this, background)) {
            resetRoundedCorners();
            return;
        }
        RoundedCornerEnforcement.computeRoundedRectangle(this, background, mEnforcedRectangle);
        setOutlineProvider(mCornerRadiusEnforcementOutline);
        mIsCornerRadiusEnforced = true;
        invalidateOutline();
    }

    public float getEnforcedCornerRadius() {
        return mEnforcedCornerRadius;
    }

    public boolean hasEnforcedCornerRadius() {
        return mIsCornerRadiusEnforced;
    }
}
