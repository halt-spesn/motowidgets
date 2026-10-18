package com.discoveryscreen.widget;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class WidgetLongPressHelper {
    private final View mView;
    private final View.OnLongClickListener mListener;
    private final float mSlop;
    /**
     * Some OEMs (this one included) ship a 300ms long-press timeout, which
     * makes the edit chrome appear almost instantly. Keep a floor so the hold
     * always feels intentional regardless of the system setting.
     */
    private static final long MIN_LONG_PRESS_TIMEOUT_MS = 550L;
    private float mLongPressTimeoutFactor = 0.75f;
    private Runnable mPendingCheck;
    private boolean mHasPerformedLongPress;
    private float mDownX;
    private float mDownY;

    public WidgetLongPressHelper(View view, View.OnLongClickListener listener) {
        mView = view;
        mListener = listener;
        mSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    public void setLongPressTimeoutFactor(float factor) {
        if (Float.isNaN(factor) || Float.isInfinite(factor)) {
            mLongPressTimeoutFactor = 0.75f;
        } else {
            mLongPressTimeoutFactor = Math.max(0.1f, Math.min(2f, factor));
        }
    }

    public long getLongPressTimeout() {
        return Math.max(MIN_LONG_PRESS_TIMEOUT_MS,
                (long) (ViewConfiguration.getLongPressTimeout() * mLongPressTimeoutFactor));
    }

    public boolean hasPerformedLongPress() {
        return mHasPerformedLongPress;
    }

    public void onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelLongPress();
            mDownX = ev.getX();
            mDownY = ev.getY();
            if (isStylusButtonPressed(ev) || isMouseRightClick(ev)) {
                triggerLongPress();
                return;
            }
            postCheckForLongPress();
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // A second finger makes ownership of the hold ambiguous. Do not
            // let a multi-touch gesture accidentally open card controls.
            cancelLongPress();
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - mDownX;
            float dy = ev.getY() - mDownY;
            if (dx * dx + dy * dy > mSlop * mSlop) {
                if (!mHasPerformedLongPress) {
                    cancelPending();
                }
            } else if (isStylusButtonPressed(ev)) {
                triggerLongPress();
            }
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            cancelLongPress();
        }
    }

    public void cancelLongPress() {
        mHasPerformedLongPress = false;
        cancelPending();
    }

    private void postCheckForLongPress() {
        mHasPerformedLongPress = false;
        if (mPendingCheck == null) {
            mPendingCheck = this::triggerLongPress;
        }
        mView.postDelayed(mPendingCheck, getLongPressTimeout());
    }

    private void cancelPending() {
        if (mPendingCheck != null) {
            mView.removeCallbacks(mPendingCheck);
            mPendingCheck = null;
        }
    }

    private void triggerLongPress() {
        mPendingCheck = null;
        if (mHasPerformedLongPress) {
            return;
        }
        boolean handled = false;
        if (mListener != null) {
            handled = mListener.onLongClick(mView);
        } else {
            handled = mView.performLongClick();
        }
        if (handled) {
            mHasPerformedLongPress = true;
        }
        cancelPending();
    }

    private static boolean isStylusButtonPressed(MotionEvent ev) {
        return ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && ev.isButtonPressed(MotionEvent.BUTTON_SECONDARY);
    }

    private static boolean isMouseRightClick(MotionEvent ev) {
        return ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                && ((ev.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0);
    }
}
