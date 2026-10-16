package com.discoveryscreen.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterViewAnimator;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/**
 * Custom ScrollView that detects nested scrollable widgets (such as Musicolet's playlist,
 * email lists, task lists, or calendar agendas) and permits them to scroll their internal
 * contents without the parent -1 screen shelf stealing touch events.
 */
public class ShelfScrollView extends ScrollView {
    private static final String TAG = "ShelfScrollView";

    private View mScrollableChildUnderTouch = null;
    private float mDownX;
    private float mDownY;
    private int mTouchSlop;
    private final int[] mHitTestLoc = new int[2];

    public ShelfScrollView(Context context) {
        super(context);
        init(context);
    }

    public ShelfScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ShelfScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_ALWAYS);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                mScrollableChildUnderTouch = findScrollableChildAt(this, mDownX, mDownY);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mScrollableChildUnderTouch = null;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                mScrollableChildUnderTouch = findScrollableChildAt(this, mDownX, mDownY);
                super.onInterceptTouchEvent(ev);
                // Return false so child view receives ACTION_DOWN
                return false;

            case MotionEvent.ACTION_MOVE:
                if (mScrollableChildUnderTouch != null) {
                    float dy = ev.getRawY() - mDownY;
                    float dx = ev.getRawX() - mDownX;

                    // If user is scrolling predominantly vertically
                    if (Math.abs(dy) > mTouchSlop && Math.abs(dy) >= Math.abs(dx)) {
                        // dy < 0 means finger is moving UP (content scrolls down, direction = 1)
                        // dy > 0 means finger is moving DOWN (content scrolls up, direction = -1)
                        int direction = dy < 0 ? 1 : -1;
                        if (canViewScrollVertical(mScrollableChildUnderTouch, direction)) {
                            // Child can scroll in this direction!
                            // Forbid this ScrollView and all parent layouts from stealing touches
                            requestDisallowInterceptTouchEvent(true);
                            return false;
                        }
                    } else if (Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy)) {
                        // Child can scroll horizontally (e.g. HorizontalScrollView / carousel)
                        int direction = dx < 0 ? 1 : -1;
                        if (canViewScrollHorizontal(mScrollableChildUnderTouch, direction)) {
                            requestDisallowInterceptTouchEvent(true);
                            return false;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mScrollableChildUnderTouch = null;
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private View findScrollableChildAt(View root, float rawX, float rawY) {
        if (root == null || root.getVisibility() != View.VISIBLE) {
            return null;
        }

        int[] loc = mHitTestLoc;
        root.getLocationOnScreen(loc);
        int x = loc[0];
        int y = loc[1];
        int w = root.getWidth();
        int h = root.getHeight();

        if (rawX < x || rawX > x + w || rawY < y || rawY > y + h) {
            return null;
        }

        // If this root itself is a scrollable container (and not this parent ShelfScrollView)
        if (isScrollableView(root)) {
            // Check if there is an even deeper nested scrollable view inside it
            if (root instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) root;
                for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                    View deeper = findScrollableChildAt(vg.getChildAt(i), rawX, rawY);
                    if (deeper != null) {
                        return deeper;
                    }
                }
            }
            return root;
        }

        // Search children in topmost-first order
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                View found = findScrollableChildAt(vg.getChildAt(i), rawX, rawY);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private boolean isScrollableView(View v) {
        if (v == null || v == this) {
            return false;
        }
        if (v instanceof AbsListView) {
            return true; // ListView, GridView
        }
        if (v instanceof ScrollView) {
            return true;
        }
        if (v instanceof HorizontalScrollView) {
            return true;
        }
        if (v instanceof AdapterViewAnimator) {
            return true; // StackView, AdapterViewFlipper
        }
        if (v instanceof AdapterView) {
            return true;
        }
        if (v.isScrollContainer()) {
            return true;
        }
        if (v.canScrollVertically(1) || v.canScrollVertically(-1) ||
            v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) {
            return true;
        }
        return false;
    }

    private boolean canViewScrollVertical(View v, int direction) {
        if (v == null) {
            return false;
        }

        // Accurate boundary check for AbsListView (e.g. Musicolet playlist ListView)
        if (v instanceof AbsListView) {
            AbsListView lv = (AbsListView) v;
            int childCount = lv.getChildCount();
            int itemCount = lv.getCount();
            if (itemCount == 0 || childCount == 0) {
                return false;
            }

            int firstPos = lv.getFirstVisiblePosition();
            int lastPos = lv.getLastVisiblePosition();

            if (direction < 0) {
                // Scrolling up (content moving down towards top of list)
                if (firstPos > 0) return true;
                View firstChild = lv.getChildAt(0);
                return firstChild != null && firstChild.getTop() < lv.getListPaddingTop();
            } else {
                // Scrolling down (content moving up towards bottom of list)
                if (lastPos < itemCount - 1) return true;
                View lastChild = lv.getChildAt(childCount - 1);
                return lastChild != null && lastChild.getBottom() > (lv.getHeight() - lv.getListPaddingBottom());
            }
        }

        // Standard Android check for ScrollView, RecyclerView, etc.
        return v.canScrollVertically(direction);
    }

    private boolean canViewScrollHorizontal(View v, int direction) {
        if (v == null) {
            return false;
        }
        return v.canScrollHorizontally(direction);
    }
}
