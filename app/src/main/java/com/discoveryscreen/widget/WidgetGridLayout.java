package com.discoveryscreen.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class WidgetGridLayout extends ViewGroup {
    public static final int GRID_COLUMNS = 4;
    public static final int[] SPAN_SIZES = {4, 2, 1};

    private int mGap;
    private int mAccent = 0xFF0A84FF;
    private float mDropRadius;
    private int mDropRow = -1;
    private int mDropCol = 0;
    private int mDropSpan = 1;
    private int mDropHeight = 0;
    private WidgetCardView mDropExcludedCard;
    private Paint mDropFill;
    private Paint mDropStroke;
    private final java.util.ArrayList<WidgetCardView> mNudged = new java.util.ArrayList<>();
    // Preview is recomputed for every drag event. Remember the destination so
    // an in-flight animation toward that destination is not restarted per move.
    private final java.util.IdentityHashMap<WidgetCardView, float[]> mNudgeTargets =
            new java.util.IdentityHashMap<>();
    // Each column advances independently. A tall half-width widget must not
    // reserve the space below its short neighbour in the other half.
    private final android.util.SparseArray<int[]> mRowColumnHeights = new android.util.SparseArray<>();
    private final android.util.SparseArray<int[]> mRowStarts = new android.util.SparseArray<>();
    private final java.util.IdentityHashMap<View, Integer> mChildTops = new java.util.IdentityHashMap<>();
    private int[] mColumnEnds = new int[GRID_COLUMNS];

    /** Layout params that also carry {@code android:layout_gravity}. */
    public static class GridLayoutParams extends MarginLayoutParams {
        public int gravity = Gravity.NO_GRAVITY;

        public GridLayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs,
                    new int[]{android.R.attr.layout_gravity});
            try {
                gravity = a.getInt(0, Gravity.NO_GRAVITY);
            } finally {
                a.recycle();
            }
        }

        public GridLayoutParams(int width, int height) {
            super(width, height);
        }

        public GridLayoutParams(MarginLayoutParams source) {
            super(source);
            if (source instanceof GridLayoutParams) {
                gravity = ((GridLayoutParams) source).gravity;
            }
        }
    }

    public WidgetGridLayout(Context context) {
        super(context);
        init(context);
    }

    public WidgetGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mGap = WidgetSpacing.borderSpacingPx(context);
        setClipChildren(false);
        mDropRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        if (mDropRadius <= 0f) {
            mDropRadius = 16f * context.getResources().getDisplayMetrics().density;
        }
        mDropFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDropFill.setStyle(Paint.Style.FILL);
        mDropStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDropStroke.setStyle(Paint.Style.STROKE);
        mDropStroke.setStrokeWidth(2f * context.getResources().getDisplayMetrics().density);
        applyAccent();
    }

    private void applyAccent() {
        mDropFill.setColor((mAccent & 0x00FFFFFF) | (0x38000000));
        mDropStroke.setColor((mAccent & 0x00FFFFFF) | (0xB4000000));
    }

    public void setAccentColor(int color) {
        if (color != mAccent) {
            mAccent = color;
            applyAccent();
            invalidate();
        }
    }

    /** Highlight the cell a dragged/resized card will occupy. */
    public void setDropTarget(int row, int col, int span, int heightPx) {
        setDropTarget(row, col, span, heightPx, null);
    }

    /** Highlight a target while keeping the card being resized out of its own preview. */
    public void setDropTarget(int row, int col, int span, int heightPx,
                              WidgetCardView excludedCard) {
        int nextCol = Math.max(0, Math.min(col, GRID_COLUMNS - 1));
        int nextSpan = Math.max(1, Math.min(span, GRID_COLUMNS));
        int nextHeight = Math.max(0, heightPx);
        if (mDropRow == row && mDropCol == nextCol && mDropSpan == nextSpan
                && mDropHeight == nextHeight && mDropExcludedCard == excludedCard) {
            return;
        }
        mDropRow = row;
        mDropCol = nextCol;
        mDropSpan = nextSpan;
        mDropHeight = nextHeight;
        mDropExcludedCard = excludedCard;
        updateReorderPreview();
        invalidate();
    }

    public void clearDropTarget() {
        resetNudges(false);
        if (mDropRow != -1) {
            mDropRow = -1;
            mDropExcludedCard = null;
            invalidate();
        }
    }

    /** Clear a committed preview while leaving its current transforms for reflow to animate from. */
    public void clearDropTargetForReflow() {
        for (WidgetCardView card : mNudged) {
            card.animate().cancel();
            mNudgeTargets.remove(card);
        }
        mNudged.clear();
        if (mDropRow != -1) {
            mDropRow = -1;
            mDropExcludedCard = null;
            invalidate();
        }
    }

    /**
     * Live "reorder preview": nudge the cards that the current drop footprint
     * would displace, mirroring Launcher3's {@code CellLayout.ReorderPreviewAnimation}
     * (magnitude = iconSizePx * 0.12, 300ms).
     */
    private void updateReorderPreview() {
        int cell = getCellWidthPx();
        if (mDropRow < 0 || cell <= 0) {
            return;
        }
        float magnitude = cell * 0.12f;
        java.util.ArrayList<WidgetCardView> targets = new java.util.ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof WidgetCardView)) {
                continue;
            }
            WidgetCardView card = (WidgetCardView) child;
            if (card == mDropExcludedCard || card.getVisibility() != VISIBLE || card.isDragging()
                    || !footprintsOverlap(card, mDropRow, mDropCol, mDropSpan)) {
                continue;
            }
            targets.add(card);
            float centerCol = mDropCol + (mDropSpan - 1) / 2f;
            float cardCol = card.getGridCellX() + (card.getGridSpanX() - 1) / 2f;
            float dx = 0f;
            float dy = 0f;
            if (cardCol < centerCol) {
                dx = -magnitude;
            } else if (cardCol > centerCol) {
                dx = magnitude;
            } else if (card.getGridRow() < mDropRow) {
                dy = -magnitude;
            } else if (card.getGridRow() > mDropRow) {
                dy = magnitude;
            }
            animateNudge(card, dx, dy);
        }
        for (int i = mNudged.size() - 1; i >= 0; i--) {
            WidgetCardView card = mNudged.get(i);
            if (!targets.contains(card)) {
                if (card == mDropExcludedCard) {
                    // Drop any old preview transform once, before the resize
                    // gesture starts using translationX to anchor the left edge.
                    if (mNudgeTargets.containsKey(card)) {
                        card.animate().cancel();
                        card.setTranslationX(0f);
                        card.setTranslationY(0f);
                    }
                    mNudgeTargets.remove(card);
                } else if (card.isDragging()) {
                    // Gesture translation is controlled by the finger; a
                    // preview settle would fight drag or left-edge resizing.
                    mNudgeTargets.remove(card);
                } else {
                    animateNudge(card, 0f, 0f);
                }
            }
        }
        mNudged.clear();
        mNudged.addAll(targets);
    }

    private boolean footprintsOverlap(WidgetCardView card, int row, int col, int span) {
        if (card.getGridRow() != row) {
            return false;
        }
        int a0 = card.getGridCellX();
        int a1 = a0 + card.getGridSpanX() - 1;
        int b0 = Math.max(0, col);
        int b1 = Math.min(GRID_COLUMNS - 1, col + span - 1);
        return a0 <= b1 && b0 <= a1;
    }

    private void animateNudge(WidgetCardView card, float x, float y) {
        float[] target = mNudgeTargets.get(card);
        if (target != null && target[0] == x && target[1] == y) {
            return;
        }
        mNudgeTargets.put(card, new float[]{x, y});
        card.animate().cancel();
        card.animate().translationX(x).translationY(y)
                .setDuration(300L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .start();
    }

    private void resetNudges(boolean animate) {
        for (WidgetCardView card : mNudged) {
            if (animate) {
                animateNudge(card, 0f, 0f);
            } else {
                card.animate().cancel();
                card.setTranslationX(0f);
                card.setTranslationY(0f);
                mNudgeTargets.remove(card);
            }
        }
        mNudged.clear();
    }

    /** Column (0..GRID_COLUMNS-1) under a grid-local x, independent of span. */
    public int colForX(float x) {
        int cell = getCellWidthPx();
        if (cell <= 0) {
            return 0;
        }
        int c = (int) Math.floor((x - getPaddingLeft() + mGap / 2f) / (cell + mGap));
        return Math.max(0, Math.min(c, GRID_COLUMNS - 1));
    }

    /**
     * Left edge of the band a dragged card may occupy. Columns are exactly {@code GRID_COLUMNS}
     * wide and row 0 is the top edge, so travel past the left, right, or top edge has nowhere to
     * drop and is a "pull past the limit" case for {@link MotionMath#pullPastLimit}. The bottom
     * edge is deliberately not limited: a drop below the last row creates a new row there, and the
     * shelf auto-scrolls to reach it.
     */
    public float dragLimitLeftPx() {
        return getPaddingLeft();
    }

    public float dragLimitRightPx(int cardWidthPx) {
        return Math.max(dragLimitLeftPx(), getWidth() - getPaddingRight() - cardWidthPx);
    }

    public float dragLimitTopPx() {
        return getPaddingTop();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new GridLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        if (p instanceof GridLayoutParams) {
            return new GridLayoutParams((GridLayoutParams) p);
        }
        return new GridLayoutParams(new MarginLayoutParams(p));
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new GridLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    private static int gravityOf(LayoutParams lp) {
        if (lp instanceof GridLayoutParams) {
            return ((GridLayoutParams) lp).gravity;
        }
        if (lp instanceof LinearLayout.LayoutParams) {
            return ((LinearLayout.LayoutParams) lp).gravity;
        }
        return Gravity.NO_GRAVITY;
    }

    public int getGridGapPx() {
        return mGap;
    }

    public int getCellWidthPx() {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        if (w <= 0) {
            return -1;
        }
        return (w - (GRID_COLUMNS - 1) * mGap) / GRID_COLUMNS;
    }

    public int spanWidthPx(int span) {
        int cell = getCellWidthPx();
        if (cell < 0) {
            return -1;
        }
        span = Math.max(1, Math.min(span, GRID_COLUMNS));
        return span * cell + (span - 1) * mGap;
    }

    /** Return the grid span whose rendered width is closest to the live pixel width. */
    public int spanForWidthPx(int widthPx) {
        int bestSpan = 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int span = 1; span <= GRID_COLUMNS; span++) {
            int distance = Math.abs(widthPx - spanWidthPx(span));
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestSpan = span;
            }
        }
        return bestSpan;
    }

    public int[] cellForPoint(float x, float y, int spanX) {
        spanX = Math.max(1, Math.min(spanX, GRID_COLUMNS));
        int col = 0;
        int cell = getCellWidthPx();
        if (cell > 0) {
            col = (int) Math.floor((x - getPaddingLeft() + mGap / 2f) / (cell + mGap));
            col = Math.max(0, Math.min(col, GRID_COLUMNS - spanX));
        }
        return new int[]{rowForY(y, col, spanX), col};
    }

    /**
     * Row index at a grid-local {@code y}. Empty (preserved) rows count as
     * droppable slots, so a widget can be put back into a gap it left behind.
     */
    public int rowForY(float y) {
        return rowForY(y, 0, GRID_COLUMNS);
    }

    private int rowForY(float y, int col, int span) {
        java.util.TreeMap<Integer, java.util.ArrayList<View>> rows = rowMap();
        if (rows.isEmpty()) {
            return 0;
        }
        if (y < getPaddingTop()) {
            return 0;
        }
        int last = rows.lastKey();
        for (int index = 0; index <= last; index++) {
            int bottom = rowTop(index, col, span);
            java.util.ArrayList<View> row = rows.get(index);
            if (row == null) {
                int[] remembered = mRowColumnHeights.get(index);
                int[] starts = mRowStarts.get(index);
                if (remembered != null && starts != null) {
                    for (int c = col; c < col + span; c++) {
                        bottom = Math.max(bottom, starts[c] + Math.max(0, remembered[c] - mGap));
                    }
                }
            } else {
                for (View child : row) {
                    int childCol = childColumn(child);
                    if (childCol < col + span && col < childCol + childSpan(child)) {
                        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                        bottom = Math.max(bottom, childTop(child) + child.getMeasuredHeight() + lp.bottomMargin);
                    }
                }
            }
            if (y <= bottom) {
                return index;
            }
        }
        return last + 1;
    }

    private static int childSpan(View child) {
        return child instanceof WidgetCardView ? ((WidgetCardView) child).getGridSpanX() : GRID_COLUMNS;
    }

    private static int childColumn(View child) {
        return child instanceof WidgetCardView ? ((WidgetCardView) child).getGridCellX() : 0;
    }

    private int childTop(View child) {
        Integer top = mChildTops.get(child);
        return top != null ? top : child.getTop();
    }

    private int rowTop(int row, int col, int span) {
        int[] starts = mRowStarts.get(row);
        if (starts == null) starts = mColumnEnds;
        int top = getPaddingTop();
        for (int c = col; c < col + span; c++) {
            top = Math.max(top, starts[c]);
        }
        return top;
    }

    /**
     * Children grouped by row index, in ascending order. Non-card children
     * (placeholder, add button) get their own rows after the cards. Row indices
     * are kept as-is so free placement can leave gaps.
     */
    private java.util.TreeMap<Integer, java.util.ArrayList<View>> rowMap() {
        java.util.TreeMap<Integer, java.util.ArrayList<View>> map = new java.util.TreeMap<>();
        java.util.ArrayList<View> rest = new java.util.ArrayList<>();
        int maxCardRow = -1;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (child instanceof WidgetCardView) {
                int row = Math.max(0, ((WidgetCardView) child).getGridRow());
                java.util.ArrayList<View> list = map.get(row);
                if (list == null) {
                    list = new java.util.ArrayList<>();
                    map.put(row, list);
                }
                list.add(child);
                maxCardRow = Math.max(maxCardRow, row);
            } else {
                rest.add(child);
            }
        }
        int next = maxCardRow + 1;
        for (View v : rest) {
            java.util.ArrayList<View> single = new java.util.ArrayList<>();
            single.add(v);
            map.put(next++, single);
        }
        return map;
    }

    private int measureCellWidth(int widthSize) {
        int content = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        return (content - (GRID_COLUMNS - 1) * mGap) / GRID_COLUMNS;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int content = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        int cell = measureCellWidth(widthSize);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int widthSpec;
            if (child instanceof WidgetCardView) {
                int span = Math.max(1, Math.min(
                        ((WidgetCardView) child).getGridSpanX(), GRID_COLUMNS));
                int w = span >= GRID_COLUMNS ? content : span * cell + (span - 1) * mGap;
                if (lp.width > 0) {
                    // A live resize sets an explicit pixel width; that must win
                    // until the snapped span is committed on touch-up.
                    w = lp.width;
                }
                widthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, w), MeasureSpec.EXACTLY);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                widthSpec = MeasureSpec.makeMeasureSpec(
                        Math.max(0, content - lp.leftMargin - lp.rightMargin),
                        MeasureSpec.EXACTLY);
            } else if (lp.width >= 0) {
                widthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            } else {
                widthSpec = MeasureSpec.makeMeasureSpec(
                        Math.max(0, content - lp.leftMargin - lp.rightMargin),
                        MeasureSpec.AT_MOST);
            }
            int heightSpec;
            if (lp.height >= 0) {
                heightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            } else {
                heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
            child.measure(widthSpec, heightSpec);
        }
        int total = computeChildTops();
        setMeasuredDimension(widthSize, resolveSize(total, heightMeasureSpec));
    }

    /** Lay out in saved row order, using only the columns each child occupies. */
    private int computeChildTops() {
        java.util.TreeMap<Integer, java.util.ArrayList<View>> rows = rowMap();
        mChildTops.clear();
        mRowStarts.clear();
        int[] nextTop = new int[GRID_COLUMNS];
        java.util.Arrays.fill(nextTop, getPaddingTop());
        if (!rows.isEmpty()) {
            for (int rowIndex = 0; rowIndex <= rows.lastKey(); rowIndex++) {
                int[] starts = nextTop.clone();
                mRowStarts.put(rowIndex, starts);
                java.util.ArrayList<View> row = rows.get(rowIndex);
                if (row == null) {
                    // Preserve a deliberately emptied row in the columns it used.
                    int[] remembered = mRowColumnHeights.get(rowIndex);
                    if (remembered != null) {
                        for (int c = 0; c < GRID_COLUMNS; c++) nextTop[c] += remembered[c];
                    }
                    continue;
                }
                for (View child : row) {
                    int col = childColumn(child);
                    int span = childSpan(child);
                    int top = getPaddingTop();
                    for (int c = col; c < col + span; c++) top = Math.max(top, nextTop[c]);
                    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    mChildTops.put(child, top + lp.topMargin);
                    int bottom = top + lp.topMargin + child.getMeasuredHeight() + lp.bottomMargin + mGap;
                    for (int c = col; c < col + span; c++) nextTop[c] = bottom;
                }
                int[] consumed = new int[GRID_COLUMNS];
                for (int c = 0; c < GRID_COLUMNS; c++) consumed[c] = nextTop[c] - starts[c];
                mRowColumnHeights.put(rowIndex, consumed);
            }
        }
        mColumnEnds = nextTop;
        int bottom = getPaddingTop();
        for (int end : nextTop) bottom = Math.max(bottom, end - mGap);
        return bottom + getPaddingBottom();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int content = Math.max(0, (r - l) - getPaddingLeft() - getPaddingRight());
        int cell = measureCellWidth(r - l);
        computeChildTops();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int left;
            if (child instanceof WidgetCardView) {
                WidgetCardView card = (WidgetCardView) child;
                int span = Math.max(1, Math.min(card.getGridSpanX(), GRID_COLUMNS));
                int col = Math.max(0, Math.min(card.getGridCellX(), GRID_COLUMNS - span));
                left = getPaddingLeft() + col * (cell + mGap) + lp.leftMargin;
            } else {
                int available = Math.max(0, content - lp.leftMargin - lp.rightMargin);
                int w = child.getMeasuredWidth();
                int gravity = Gravity.getAbsoluteGravity(gravityOf(lp), getLayoutDirection())
                        & Gravity.HORIZONTAL_GRAVITY_MASK;
                if (gravity == Gravity.CENTER_HORIZONTAL) {
                    left = getPaddingLeft() + lp.leftMargin + (available - w) / 2;
                } else if (gravity == Gravity.RIGHT) {
                    left = getPaddingLeft() + content - lp.rightMargin - w;
                } else {
                    left = getPaddingLeft() + lp.leftMargin;
                }
            }
            int top = childTop(child);
            child.layout(left, top, left + child.getMeasuredWidth(),
                    top + child.getMeasuredHeight());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mDropRow < 0) {
            return;
        }
        RectF rect = dropTargetRect();
        if (rect == null) {
            return;
        }
        canvas.drawRoundRect(rect, mDropRadius, mDropRadius, mDropFill);
        canvas.drawRoundRect(rect, mDropRadius, mDropRadius, mDropStroke);
    }

    private RectF dropTargetRect() {
        int cell = getCellWidthPx();
        if (cell <= 0) {
            return null;
        }
        int span = Math.max(1, Math.min(mDropSpan, GRID_COLUMNS));
        int col = Math.max(0, Math.min(mDropCol, GRID_COLUMNS - span));
        float left = getPaddingLeft() + col * (cell + mGap);
        float right = left + span * cell + (span - 1) * mGap;

        float top = rowTop(mDropRow, col, span);
        // Use the available top in these columns and the dragged card's own height.
        float bottom = top + (mDropHeight > 0 ? mDropHeight : cell);
        return new RectF(left, top, right, bottom);
    }
}
