package com.discoveryscreen.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
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
    private static final float MAX_GESTURE_VELOCITY_DP = 2400f;
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

    private final ViewStateAnimator mState = new ViewStateAnimator(this);
    /** Finger travel since the drag started, on top of the card's position at that moment. */
    private float mRawDragX;
    private float mRawDragY;
    private VelocityTracker mVelocityTracker;
    private float mReleaseVelocityX;
    private float mReleaseVelocityY;

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
        mState.cancel();
        recycleVelocityTracker();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setTranslationZ(0f);
        setElevation(baseElevationPx());
        super.onDetachedFromWindow();
    }

    public void setWidgetId(int widgetId) {
        mWidgetId = widgetId;
        setTag(widgetId);
    }

    public void setTapMotionEnabled(boolean enabled) {
        mTapMotionEnabled = enabled;
        if (enabled) {
            setElevation(baseElevationPx());
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

    /**
     * Springs the card from its current translation/scale to its laid-out position, seeded with
     * the velocity the finger let go at so a flick carries into the settle instead of stopping.
     */
    public void settleStateHome(float velocityX, float velocityY, Runnable onUpdate) {
        mState.translationX(0f).translationY(0f).scaleX(1f)
                .withUpdateAction(onUpdate)
                .withEndAction(this::resetStateScalePivot)
                .springTo(SpringConfig.SNAP_BACK, velocityX, velocityY);
    }

    /**
     * Stops a settle-to-home that is still in flight, leaving the card where it is. Only the
     * translation is dropped: its scale and depth belong to the gesture drive that follows.
     */
    public void cancelSettleAnimation() {
        mState.cancel(ViewStateAnimator.TRANSLATION_X, ViewStateAnimator.TRANSLATION_Y);
    }

    /** Cancels the scale component when a timed grid reflow takes ownership of scaleX. */
    public void cancelSettleScaleAnimation() {
        mState.cancel(ViewStateAnimator.SCALE_X);
    }

    /**
     * Release velocity of the last finger drag, px/s, handed over once: the settle that follows
     * the drop is the only animation that may use it.
     */
    public boolean takeReleaseVelocity(float[] out) {
        float x = mReleaseVelocityX;
        float y = mReleaseVelocityY;
        mReleaseVelocityX = 0f;
        mReleaseVelocityY = 0f;
        if (x == 0f && y == 0f) {
            return false;
        }
        if (out != null && out.length >= 2) {
            out[0] = x;
            out[1] = y;
        }
        return true;
    }

    private void resetStateScalePivot() {
        setPivotX(getWidth() / 2f);
    }

    private float baseElevationPx() {
        return mTapMotionEnabled
                ? TAP_BASE_ELEVATION_DP * getResources().getDisplayMetrics().density : 0f;
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
                // The content moved under the finger, so the raw drag offset has to move with it
                // for the card to stay anchored; the applied translation follows from that.
                mRawDragY += scrollDelta;
                applyDragTranslation();
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
            mRawDragX = 0f;
            mRawDragY = 0f;
            trackVelocity(ev);
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
                recycleVelocityTracker();
            }
            return true;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            resetTapMotion();
            recycleVelocityTracker();
        }
        return handled;
    }

    /** A small source-card response accompanies widget taps while Android opens their target. */
    private void animateTapPress() {
        final int generation = ++mGestureAnimationGeneration;
        mTapMotionActive = true;
        float density = getResources().getDisplayMetrics().density;
        mState.scale(TAP_SCALE).translationZ(-TAP_SINK_DP * density)
                .withEndAction(() -> {
                    if (!mTapMotionActive || generation != mGestureAnimationGeneration) return;
                    mState.scale(0.974f).translationZ(-1.2f * density)
                            .animateTo(90L, Interpolators.EMPHASIZED_DECELERATE);
                })
                .animateTo(165L, Interpolators.ACCELERATE_DECELERATE);
    }

    private void resetTapMotion() {
        if (!mTapMotionActive) return;
        final int generation = ++mGestureAnimationGeneration;
        mTapMotionActive = false;
        mState.scale(1.008f).translationZ(0f)
                .withEndAction(() -> {
                    if (generation != mGestureAnimationGeneration) return;
                    mState.scale(1f).animateTo(135L, Interpolators.DECELERATE_1_5);
                })
                .animateTo(210L, Interpolators.EMPHASIZED_DECELERATE);
    }

    /** Give a recognized long press a subtle lift before showing widget controls. */
    private void animateLongPressLift() {
        final int generation = ++mGestureAnimationGeneration;
        mLongPressLiftActive = true;
        cancelLongPressPulse();
        mState.scale(1.026f).translationZ(9f * getResources().getDisplayMetrics().density)
                .withEndAction(() -> {
                    if (!mLongPressLiftActive || generation != mGestureAnimationGeneration) return;
                    mState.scale(1.018f).translationZ(8f * getResources().getDisplayMetrics().density)
                            .withEndAction(() -> {
                                if (mLongPressLiftActive
                                        && generation == mGestureAnimationGeneration) {
                                    startLongPressPulse();
                                }
                            })
                            .animateTo(155L, Interpolators.DECELERATE_1_5);
                })
                .animateTo(230L, Interpolators.EMPHASIZED_DECELERATE);
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
            float z = (8f + pulse * 0.5f) * density;
            // Written straight through, but the animator keeps the same targets so a later drive
            // or snap starts from where the pulse left the card.
            mState.scale(scale).translationZ(z);
            setScaleX(scale);
            setScaleY(scale);
            setTranslationZ(z);
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
        mState.scale(0.994f).translationZ(0f)
                .withEndAction(() -> {
                    if (generation != mGestureAnimationGeneration) return;
                    mState.scale(1f).animateTo(135L, Interpolators.DECELERATE_1_5);
                })
                .animateTo(180L, Interpolators.EMPHASIZED_DECELERATE);
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
                    beginDragVelocity();
                    notifyDragStarted();
                }
            }
            if (mDragging) {
                float dy = ev.getRawY() - mLastRawY;
                float dx = ev.getRawX() - mLastRawX;
                mLastRawY = ev.getRawY();
                mLastRawX = ev.getRawX();
                mRawDragX += dx;
                mRawDragY += dy;
                applyDragTranslation();
                addDragVelocity(ev);
                updateDropTarget();
                mDragRawY = ev.getRawY();
                scheduleAutoScroll();
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (mDragging) {
                float dx = ev.getRawX() - mLastRawX;
                float dy = ev.getRawY() - mLastRawY;
                mLastRawX = ev.getRawX();
                mLastRawY = ev.getRawY();
                mRawDragX += dx;
                mRawDragY += dy;
                applyDragTranslation();
                addDragVelocity(ev);
                updateDropTarget();
                recordReleaseVelocity();
                float totalX = ev.getRawX() - mDownRawX;
                float totalY = ev.getRawY() - mDownRawY;
                float clickSlop = mTouchSlop * 2f;
                if (totalX * totalX + totalY * totalY <= clickSlop * clickSlop) {
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

    /**
     * Maps the finger's offset onto the grid, applying the drag band: inside it the card tracks
     * the finger, past the left, right, or top edge it rubber-bands and saturates. The raw offset
     * starts at the card's translation when the drag began, so a card caught mid-settle continues
     * from where it is instead of jumping to its slot.
     */
    private void applyDragTranslation() {
        WidgetGridLayout grid = gridParent();
        if (grid == null) {
            setTranslationX(mRawDragX);
            setTranslationY(mRawDragY);
            return;
        }
        float layoutLeft = getLeft();
        float layoutTop = getTop();
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        setTranslationX(limitDragAxis(layoutLeft + mRawDragX, layoutLeft,
                grid.dragLimitLeftPx(), grid.dragLimitRightPx(Math.round(width)), width));
        setTranslationY(limitDragTop(layoutTop + mRawDragY, layoutTop,
                grid.dragLimitTopPx(), height));
    }

    private static float limitDragAxis(float desired, float base, float min, float max,
            float dimension) {
        if (desired >= min && desired <= max) {
            return desired - base;
        }
        float limited = desired < min ? min : max;
        float pull = MotionMath.pullPastLimit(Math.abs(desired - limited), dimension);
        return (desired < min ? limited - pull : limited + pull) - base;
    }

    /** Vertical drag resists only above the grid's first row; below it the shelf grows a row. */
    private static float limitDragTop(float desired, float base, float min, float dimension) {
        if (desired >= min) {
            return desired - base;
        }
        return (min - MotionMath.pullPastLimit(min - desired, dimension)) - base;
    }

    private void trackVelocity(MotionEvent ev) {
        recycleVelocityTracker();
        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(ev);
        mReleaseVelocityX = 0f;
        mReleaseVelocityY = 0f;
    }

    /** Start velocity tracking in the card's translated coordinate space. */
    private void beginDragVelocity() {
        recycleVelocityTracker();
        mVelocityTracker = VelocityTracker.obtain();
        mReleaseVelocityX = 0f;
        mReleaseVelocityY = 0f;
    }

    /** Track rendered translation so rubber-banding is reflected in the release speed. */
    private void addDragVelocity(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }
        MotionEvent translated = MotionEvent.obtain(ev);
        translated.setLocation(getTranslationX(), getTranslationY());
        mVelocityTracker.addMovement(translated);
        translated.recycle();
    }

    private void recordReleaseVelocity() {
        if (mVelocityTracker == null) {
            return;
        }
        float max = MAX_GESTURE_VELOCITY_DP * getResources().getDisplayMetrics().density;
        mVelocityTracker.computeCurrentVelocity(1000, max);
        mReleaseVelocityX = mVelocityTracker.getXVelocity();
        mReleaseVelocityY = mVelocityTracker.getYVelocity();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
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

    /**
     * Pick-up "lift": 150ms scale up + 30dp elevation (Launcher3 DragView). The elevation is
     * carried as translationZ on top of the card's base elevation, so the lift animates the
     * delta from whatever the tap motion left the card at instead of snapping to a new depth.
     */
    private void animateLift() {
        ++mGestureAnimationGeneration;
        mTapMotionActive = false;
        mLongPressLiftActive = false;
        cancelLongPressPulse();
        setPivotX(getWidth() / 2f);
        // The finger takes over the card's translation from wherever it is right now.
        mRawDragX = getTranslationX();
        mRawDragY = getTranslationY();
        mState.scale(LIFT_SCALE).translationZ(liftTranslationZPx())
                .animateTo(LIFT_DURATION_MS, Interpolators.DECELERATE_1_5);
    }

    private float liftTranslationZPx() {
        // Base elevation stays put, so the shadow depth matches the old setElevation(30dp).
        return (DRAG_ELEVATION_DP - (mTapMotionEnabled ? TAP_BASE_ELEVATION_DP : 0f))
                * getResources().getDisplayMetrics().density;
    }

    /** Drop "settle": scale back to 1 and drop the lift. */
    private void animateSettle() {
        ++mGestureAnimationGeneration;
        mLongPressLiftActive = false;
        cancelLongPressPulse();
        mState.scale(1f).translationZ(0f)
                .animateTo(LIFT_DURATION_MS, Interpolators.DECELERATE_1_5);
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
        mRawDragX = 0f;
        mRawDragY = 0f;
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
        mReleaseVelocityX = 0f;
        mReleaseVelocityY = 0f;
    }
}
