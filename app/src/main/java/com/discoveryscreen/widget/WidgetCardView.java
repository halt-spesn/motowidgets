package com.discoveryscreen.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class WidgetCardView extends LinearLayout {
    public interface Delegate {
        void onOptionsRequested(WidgetCardView card);

        void onDragStarted(WidgetCardView card);

        void onDragEnded(WidgetCardView card, int targetRow, int targetCol);
    }

    private int mWidgetId = -1;
    private Delegate mDelegate;
    private WidgetLongPressHelper mLongPressHelper;
    private int mTouchSlop;
    /** Drag lift: 150ms scale-up and 30dp elevation, ported from Launcher3's
     *  {@code DragView} ({@code mScaleAnim} duration, {@code drag_elevation}). */
    private static final long LIFT_DURATION_MS = 150L;
    private static final float LIFT_SCALE = 1.03f;
    private static final float DRAG_ELEVATION_DP = 30f;
    private float mDownRawY;
    private float mDownRawX;
    private boolean mLongPressFired;
    private boolean mOptionsRequested;
    private boolean mDragging;
    private boolean mDragNotified;
    private float mLastRawY;
    private float mLastRawX;
    private float mDragRawY;
    private boolean mAutoScrollPosted;
    private int mDropRow = -1;
    private int mDropCol = -1;
    private View mHostView;
    private int mGridRow;
    private int mGridCellX;
    private int mGridSpanX = WidgetGridLayout.GRID_COLUMNS;
    private boolean mTapMotionEnabled = true;
    private boolean mTapMotionActive;
    private boolean mLongPressLiftActive;
    private int mGestureAnimationGeneration;
    private android.animation.ValueAnimator mLongPressPulse;
    private static final float TAP_SCALE = 0.968f;
    private static final float TAP_BASE_ELEVATION_DP = 4f;
    private static final float TAP_SINK_DP = 2f;

    public WidgetCardView(Context context) {
        super(context);
        init(context);
    }

    public WidgetCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setClickable(true);
        setLongClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressHelper = new WidgetLongPressHelper(this, v -> {
            mLongPressFired = true;
            resetTapMotion();
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            animateLongPressLift();
            notifyOptionsRequested();
            // Hold the gesture for drag/selection so the ScrollView or the
            // shelf swipe detector does not steal it once the press lands.
            requestDisallowInterceptTouchEvent(true);
            return true;
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mAutoScrollFrame);
        mAutoScrollPosted = false;
        mLongPressHelper.cancelLongPress();
        ++mGestureAnimationGeneration;
        mTapMotionActive = false;
        mLongPressLiftActive = false;
        mStealing = false;
        mDragging = false;
        mDragNotified = false;
        cancelLongPressPulse();
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setTranslationZ(0f);
        setElevation(mTapMotionEnabled
                ? TAP_BASE_ELEVATION_DP * getResources().getDisplayMetrics().density : 0f);
        super.onDetachedFromWindow();
    }

    public void setWidgetId(int widgetId) {
        mWidgetId = widgetId;
        setTag(widgetId);
    }

    public void setTapMotionEnabled(boolean enabled) {
        mTapMotionEnabled = enabled;
        if (enabled) {
            setElevation(TAP_BASE_ELEVATION_DP * getResources().getDisplayMetrics().density);
        } else {
            resetTapMotion();
            setTranslationZ(0f);
            setElevation(0f);
        }
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public void setDelegate(Delegate delegate) {
        mDelegate = delegate;
    }

    public boolean isDragging() {
        return mDragging;
    }

    public void setGridPos(int row, int cellX, int spanX) {
        mGridRow = Math.max(0, row);
        mGridSpanX = Math.max(1, Math.min(spanX, WidgetGridLayout.GRID_COLUMNS));
        mGridCellX = Math.max(0, Math.min(cellX, WidgetGridLayout.GRID_COLUMNS - mGridSpanX));
    }

    public int getGridRow() {
        return mGridRow;
    }

    public int getGridCellX() {
        return mGridCellX;
    }

    public int getGridSpanX() {
        return mGridSpanX;
    }

    public void bindResizeTarget(View host) {
        mHostView = host;
    }

    public View getHostView() {
        return mHostView;
    }

    public void setCardWidthPx(int widthPx) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.width != widthPx) {
            lp.width = widthPx;
            setLayoutParams(lp);
        }
    }

    public int currentCardWidthPx() {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.width > 0) {
            return lp.width;
        }
        return Math.max(1, getWidth());
    }

    private WidgetGridLayout gridParent() {
        if (getParent() instanceof WidgetGridLayout) {
            return (WidgetGridLayout) getParent();
        }
        return null;
    }

    private void notifyDragStarted() {
        if (!mDragNotified) {
            mDragNotified = true;
            if (mDelegate != null) {
                mDelegate.onDragStarted(this);
            }
        }
    }

    private boolean mStealing;

    private final Runnable mAutoScrollFrame = new Runnable() {
        @Override
        public void run() {
            mAutoScrollPosted = false;
            if (!mDragging) return;
            ScrollView scroll = findScrollView();
            if (scroll == null) return;
            int[] loc = new int[2];
            scroll.getLocationOnScreen(loc);
            int top = loc[1];
            int bottom = top + scroll.getHeight();
            float density = getResources().getDisplayMetrics().density;
            float edge = 120f * density;
            float penetration;
            if (mDragRawY < top + edge) {
                penetration = -Math.min(1f, (top + edge - mDragRawY) / edge);
            } else if (mDragRawY > bottom - edge) {
                penetration = Math.min(1f, (mDragRawY - (bottom - edge)) / edge);
            } else {
                return;
            }
            int step = Math.round(Math.signum(penetration)
                    * (4f + 20f * Math.abs(penetration)) * density);
            int oldScrollY = scroll.getScrollY();
            scroll.scrollBy(0, step);
            int scrollDelta = scroll.getScrollY() - oldScrollY;
            if (scrollDelta != 0) {
                setTranslationY(getTranslationY() + scrollDelta);
                updateDropTarget();
                postOnAnimation(this);
                mAutoScrollPosted = true;
            }
        }
    };

    /**
     * Runs the long-press / drag / select detection here rather than in
     * {@code onInterceptTouchEvent}. {@code dispatchTouchEvent} is always
     * called, even when an interactive child inside the hosted widget owns the
     * gesture or has called {@code requestDisallowInterceptTouchEvent(true)},
     * so a long press anywhere on the card (including buttons and scrollable
     * widget content) reliably reveals the customization chrome.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownRawX = ev.getRawX();
            mDownRawY = ev.getRawY();
            mLastRawX = ev.getRawX();
            mLastRawY = ev.getRawY();
            mDragging = false;
            mDragNotified = false;
            mLongPressFired = false;
            mOptionsRequested = false;
            mStealing = false;
            mDropRow = -1;
            mDropCol = -1;
            if (mTapMotionEnabled) animateTapPress();
        } else if (action == MotionEvent.ACTION_MOVE && mTapMotionActive) {
            float dx = ev.getRawX() - mDownRawX;
            float dy = ev.getRawY() - mDownRawY;
            if (dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop) {
                resetTapMotion();
            }
        }
        mLongPressHelper.onTouchEvent(ev);
        if (mLongPressHelper.hasPerformedLongPress()) {
            mLongPressFired = true;
        }

        if (mLongPressFired && !mStealing && action != MotionEvent.ACTION_DOWN) {
            // The long press landed over widget content: cancel the child's
            // gesture (so its own click/scroll stops) and drive the card from
            // here on.
            mStealing = true;
            resetTapMotion();
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }

        if (mStealing) {
            handleCardGesture(ev, action);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mStealing = false;
                mDragging = false;
                mDragNotified = false;
                mLongPressFired = false;
                if (action == MotionEvent.ACTION_CANCEL) settleLongPressLift();
                mDropRow = -1;
                mDropCol = -1;
                requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            resetTapMotion();
        }
        return handled;
    }

    /** A small source-card response accompanies widget taps while Android opens their target. */
    private void animateTapPress() {
        final int generation = ++mGestureAnimationGeneration;
        animate().cancel();
        mTapMotionActive = true;
        animate().scaleX(TAP_SCALE).scaleY(TAP_SCALE)
                .translationZ(-TAP_SINK_DP * getResources().getDisplayMetrics().density)
                .setDuration(165L)
                .setInterpolator(Interpolators.ACCELERATE_DECELERATE)
                .withEndAction(() -> {
                    if (!mTapMotionActive || generation != mGestureAnimationGeneration) return;
                    animate().scaleX(0.974f).scaleY(0.974f)
                            .translationZ(-1.2f * getResources().getDisplayMetrics().density)
                            .setDuration(90L)
                            .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                            .start();
                })
                .start();
    }

    private void resetTapMotion() {
        if (!mTapMotionActive) return;
        final int generation = ++mGestureAnimationGeneration;
        mTapMotionActive = false;
        animate().cancel();
        animate().scaleX(1.008f).scaleY(1.008f).translationZ(0f)
                .setDuration(210L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (generation != mGestureAnimationGeneration) return;
                    animate().scaleX(1f).scaleY(1f)
                            .setDuration(135L)
                            .setInterpolator(Interpolators.DECELERATE_1_5)
                            .start();
                })
                .start();
    }

    /** Give a recognized long press a subtle lift before showing widget controls. */
    private void animateLongPressLift() {
        final int generation = ++mGestureAnimationGeneration;
        mLongPressLiftActive = true;
        cancelLongPressPulse();
        animate().cancel();
        animate().scaleX(1.026f).scaleY(1.026f)
                .translationZ(9f * getResources().getDisplayMetrics().density)
                .setDuration(230L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (!mLongPressLiftActive || generation != mGestureAnimationGeneration) return;
                    animate().scaleX(1.018f).scaleY(1.018f)
                            .translationZ(8f * getResources().getDisplayMetrics().density)
                            .setDuration(155L)
                            .setInterpolator(Interpolators.DECELERATE_1_5)
                            .withEndAction(() -> {
                                if (mLongPressLiftActive
                                        && generation == mGestureAnimationGeneration) {
                                    startLongPressPulse();
                                }
                            })
                            .start();
                })
                .start();
    }

    private void startLongPressPulse() {
        cancelLongPressPulse();
        final float density = getResources().getDisplayMetrics().density;
        final int generation = mGestureAnimationGeneration;
        mLongPressPulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
        mLongPressPulse.setDuration(1100L);
        mLongPressPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mLongPressPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        mLongPressPulse.setInterpolator(Interpolators.ACCELERATE_DECELERATE);
        mLongPressPulse.addUpdateListener(animator -> {
            if (!mLongPressLiftActive || generation != mGestureAnimationGeneration) return;
            float pulse = (float) animator.getAnimatedValue();
            float scale = 1.018f + pulse * 0.003f;
            setScaleX(scale);
            setScaleY(scale);
            setTranslationZ((8f + pulse * 0.5f) * density);
        });
        mLongPressPulse.start();
    }

    private void cancelLongPressPulse() {
        if (mLongPressPulse == null) return;
        android.animation.ValueAnimator pulse = mLongPressPulse;
        mLongPressPulse = null;
        pulse.cancel();
    }

    private void settleLongPressLift() {
        if (!mLongPressLiftActive) return;
        final int generation = ++mGestureAnimationGeneration;
        mLongPressLiftActive = false;
        cancelLongPressPulse();
        animate().cancel();
        animate().scaleX(0.994f).scaleY(0.994f).translationZ(0f)
                .setDuration(180L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (generation != mGestureAnimationGeneration) return;
                    animate().scaleX(1f).scaleY(1f)
                            .setDuration(135L)
                            .setInterpolator(Interpolators.DECELERATE_1_5)
                            .start();
                })
                .start();
    }

    private void handleCardGesture(MotionEvent ev, int action) {
        if (action == MotionEvent.ACTION_MOVE) {
            if (!mDragging) {
                float dx = ev.getRawX() - mDownRawX;
                float dy = ev.getRawY() - mDownRawY;
                if (dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop) {
                    mDragging = true;
                    mTapMotionActive = false;
                    bringToFront();
                    requestDisallowInterceptTouchEvent(true);
                    animateLift();
                    notifyDragStarted();
                }
            }
            if (mDragging) {
                float dy = ev.getRawY() - mLastRawY;
                float dx = ev.getRawX() - mLastRawX;
                mLastRawY = ev.getRawY();
                mLastRawX = ev.getRawX();
                setTranslationY(getTranslationY() + dy);
                setTranslationX(getTranslationX() + dx);
                updateDropTarget();
                mDragRawY = ev.getRawY();
                scheduleAutoScroll();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (mDragging) {
                float dx = ev.getRawX() - mDownRawX;
                float dy = ev.getRawY() - mDownRawY;
                float clickSlop = mTouchSlop * 2f;
                if (dx * dx + dy * dy <= clickSlop * clickSlop) {
                    cancelSmallDrag();
                    notifyOptionsRequested();
                } else {
                    finishDrag();
                }
            } else {
                settleLongPressLift();
                notifyOptionsRequested();
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            if (mDragging) {
                finishDrag();
            }
        }
    }

    private void notifyOptionsRequested() {
        if (mOptionsRequested) return;
        mOptionsRequested = true;
        if (mDelegate != null) {
            final Delegate delegate = mDelegate;
            final WidgetCardView self = this;
            post(() -> delegate.onOptionsRequested(self));
        }
    }

    /** Pick-up "lift": 150ms scale up + 30dp elevation (Launcher3 DragView). */
    private void animateLift() {
        ++mGestureAnimationGeneration;
        mTapMotionActive = false;
        mLongPressLiftActive = false;
        cancelLongPressPulse();
        setTranslationZ(0f);
        setElevation(DRAG_ELEVATION_DP * getResources().getDisplayMetrics().density);
        animate().cancel();
        animate().scaleX(LIFT_SCALE).scaleY(LIFT_SCALE)
                .setDuration(LIFT_DURATION_MS)
                .setInterpolator(Interpolators.DECELERATE_1_5)
                .start();
    }

    /** Drop "settle": scale back to 1 and drop the elevation. */
    private void animateSettle() {
        ++mGestureAnimationGeneration;
        mLongPressLiftActive = false;
        cancelLongPressPulse();
        animate().cancel();
        animate().scaleX(1f).scaleY(1f).translationZ(0f)
                .setDuration(LIFT_DURATION_MS)
                .setInterpolator(Interpolators.DECELERATE_1_5)
                .start();
        setElevation(0f);
    }

    private void updateDropTarget() {
        WidgetGridLayout grid = gridParent();
        if (grid == null) {
            mDropRow = -1;
            mDropCol = -1;
            return;
        }
        float cx = getLeft() + getTranslationX() + (float) getWidth() / 2f;
        float cy = getTop() + getTranslationY() + (float) getHeight() / 2f;
        mDropRow = grid.rowForY(cy);
        mDropCol = grid.colForX(cx);
        grid.setDropTarget(mDropRow, mDropCol, mGridSpanX, getHeight());
    }

    private ScrollView findScrollView() {
        View p = (View) getParent();
        while (p != null) {
            if (p instanceof ScrollView) {
                return (ScrollView) p;
            }
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    private void scheduleAutoScroll() {
        if (!mAutoScrollPosted) {
            postOnAnimation(mAutoScrollFrame);
            mAutoScrollPosted = true;
        }
    }

    private void stopAutoScroll() {
        removeCallbacks(mAutoScrollFrame);
        mAutoScrollPosted = false;
    }

    private void finishDrag() {
        stopAutoScroll();
        int targetRow = mDropRow;
        int targetCol = mDropCol;
        WidgetGridLayout grid = gridParent();
        if (grid != null) {
            grid.clearDropTarget();
        }
        animateSettle();
        requestDisallowInterceptTouchEvent(false);
        if (targetRow < 0 || targetCol < 0) {
            updateDropTarget();
            targetRow = mDropRow;
            targetCol = mDropCol;
        }
        // Always notify so the host can clear its gesture state, even when the
        // card was dropped outside a valid cell.
        if (mDelegate != null) {
            mDelegate.onDragEnded(this, targetRow, targetCol);
        }
        mDragging = false;
        mDragNotified = false;
        mLongPressFired = false;
        mDropRow = -1;
        mDropCol = -1;
    }

    /** Treat tiny post-long-press finger jitter as selection, not a grid move. */
    private void cancelSmallDrag() {
        stopAutoScroll();
        WidgetGridLayout grid = gridParent();
        if (grid != null) {
            grid.clearDropTarget();
        }
        setTranslationX(0f);
        setTranslationY(0f);
        animateSettle();
        requestDisallowInterceptTouchEvent(false);
        if (mDelegate != null) {
            mDelegate.onDragEnded(this, -1, -1);
        }
        mDragging = false;
        mDragNotified = false;
        mLongPressFired = false;
        mDropRow = -1;
        mDropCol = -1;
    }
}
