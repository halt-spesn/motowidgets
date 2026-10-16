package com.discoveryscreen.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.animation.ValueAnimator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Draggable resize chrome drawn on top of the shelf.
 *
 * <p>The widget rect is the card bounds. The frame stroke is drawn a couple of
 * pixels outside the widget (mirroring the launcher, whose 25dp padded frame
 * view carries a 23dp inset drawable, i.e. the visible stroke lands at the
 * widget edge). Handles sit on that stroke and the touch band straddles the
 * widget edge instead of being pushed 23dp away from it.</p>
 */
public class WidgetResizeFrame extends View {
    public static final int EDGE_LEFT = 1;
    public static final int EDGE_RIGHT = 2;
    public static final int EDGE_TOP = 4;
    public static final int EDGE_BOTTOM = 8;

    public static final int BUTTON_RECONFIGURE = 1;
    public static final int BUTTON_REMOVE = 2;

    public static final int PRESET_SMALL = 0;
    public static final int PRESET_MEDIUM = 1;
    public static final int PRESET_LARGE = 2;

    public static final int BUTTON_PRESET_BASE = 10;
    public interface Listener {
        void onBorderDown(int edges);

        void onBorderMove(int edges, float dx, float dy);

        void onBorderUp(int edges, boolean commit);

        void onButtonTap(int button);

        void onPresetSelected(int preset);

        void onTapOutside();
    }

    private Listener mListener;
    private final RectF mWidget = new RectF();
    private final RectF mFrame = new RectF();
    private final RectF mBtnReconfigure = new RectF();
    private final RectF mBtnRemove = new RectF();
    private final RectF mBtnRemoveTouch = new RectF();
    private boolean mHasTarget;
    private boolean mHasReconfigure;
    private int mAccent = 0xFF0A84FF;
    private float mRadius;
    private float mBand;
    private float mInset;
    private float mStroke;
    private float mHandleR;
    private float mBtnSize;
    private float mBtnMargin;
    private float mBtnCorner;
    private float mRemoveSize;
    private float mRemoveMargin;
    private boolean mRemovePressed;
    private final RectF[] mPresets = new RectF[]{new RectF(), new RectF(), new RectF()};
    private final String[] mPresetLabels = new String[]{"2\u00d72", "4\u00d72", "4\u00d74"};
    private boolean mHasPresets = true;
    private float mPresetW;
    private float mPresetH;
    private float mPresetGap;
    private int mDownPreset = -1;
    private int mActiveEdges;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;
    private int mResizableEdges = EDGE_LEFT | EDGE_RIGHT | EDGE_TOP | EDGE_BOTTOM;
    private int mDownButton;
    private float mRevealAlpha = 1f;
    private ValueAnimator mRevealAnim;
    private float mDownX;
    private float mDownY;
    private Paint mFramePaint;
    private Paint mHandlePaint;
    private Paint mBtnPaint;
    private Paint mBtnIconPaint;
    private Paint mBtnHolePaint;
    private Paint mRemovePaint;
    private Paint mRemoveStrokePaint;
    private Paint mLinePaint;
    private Paint mPresetPaint;
    private Paint mPresetTextPaint;

    public WidgetResizeFrame(Context context) {
        super(context);
        init(context);
    }

    public WidgetResizeFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        float density = context.getResources().getDisplayMetrics().density;
        mStroke = 2f * density;
        mInset = mStroke;
        mBand = 24f * density;
        mHandleR = 9.5f * density;
        mBtnSize = 36f * density;
        mBtnMargin = 32f * density;
        mBtnCorner = 14f * density;
        mRemoveSize = 28f * density;
        mRemoveMargin = 12f * density;
        mPresetW = 52f * density;
        mPresetH = 34f * density;
        mPresetGap = 8f * density;
        mRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        if (mRadius <= 0f) {
            mRadius = 16f * density;
        }
        rebuildPaints();
    }

    private void rebuildPaints() {
        mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFramePaint.setStyle(Paint.Style.STROKE);
        mFramePaint.setColor(mAccent);
        mFramePaint.setStrokeWidth(mStroke);
        mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHandlePaint.setStyle(Paint.Style.FILL);
        mHandlePaint.setColor(mAccent);
        mBtnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBtnPaint.setStyle(Paint.Style.FILL);
        mBtnPaint.setColor(mAccent);
        mBtnIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBtnIconPaint.setStyle(Paint.Style.FILL);
        mBtnIconPaint.setColor(0xFFFFFFFF);
        mBtnHolePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBtnHolePaint.setStyle(Paint.Style.FILL);
        mBtnHolePaint.setColor(mAccent);
        mRemovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRemovePaint.setStyle(Paint.Style.FILL);
        mRemovePaint.setColor(mAccent);
        mRemoveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRemoveStrokePaint.setStyle(Paint.Style.STROKE);
        mRemoveStrokePaint.setColor(0x40FFFFFF);
        mRemoveStrokePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setColor(Color.WHITE);
        mLinePaint.setStrokeWidth(1.8f * getResources().getDisplayMetrics().density);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mPresetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPresetPaint.setStyle(Paint.Style.FILL);
        mPresetPaint.setColor(mAccent);
        mPresetTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPresetTextPaint.setColor(0xFFFFFFFF);
        mPresetTextPaint.setTextAlign(Paint.Align.CENTER);
        mPresetTextPaint.setTextSize(11f * getResources().getDisplayMetrics().density);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setAccentColor(int color) {
        if (mAccent != color) {
            mAccent = color;
            rebuildPaints();
            invalidate();
        }
    }

    public void setCornerRadius(float radius) {
        if (radius > 0f && mRadius != radius) {
            mRadius = radius;
            invalidate();
        }
    }

    public void setReconfigureVisible(boolean visible) {
        if (mHasReconfigure != visible) {
            mHasReconfigure = visible;
            invalidate();
        }
    }

    public void setResizableEdges(int edges) {
        int allowed = EDGE_LEFT | EDGE_RIGHT | EDGE_TOP | EDGE_BOTTOM;
        edges &= allowed;
        if (mResizableEdges != edges) {
            mResizableEdges = edges;
            invalidate();
        }
    }

    public void setTargetRect(float left, float top, float right, float bottom) {
        mWidget.set(left, top, right, bottom);
        mFrame.set(left - mInset, top - mInset, right + mInset, bottom + mInset);
        mBtnReconfigure.set(right - mBtnMargin - mBtnSize, bottom - mBtnMargin - mBtnSize,
                right - mBtnMargin, bottom - mBtnMargin);
        // Center the compact action on the actual selection-outline stroke,
        // rather than positioning it from the card edge like the widget content.
        float removeCenterX = mFrame.right - mRemoveMargin;
        float removeCenterY = mFrame.top;
        float removeLeft = removeCenterX - mRemoveSize / 2f;
        float removeTop = removeCenterY - mRemoveSize / 2f;
        mBtnRemove.set(removeLeft, removeTop,
                removeLeft + mRemoveSize, removeTop + mRemoveSize);
        float touchInset = 8f * getResources().getDisplayMetrics().density;
        mBtnRemoveTouch.set(mBtnRemove.left - touchInset, mBtnRemove.top - touchInset,
                mBtnRemove.right + touchInset, mBtnRemove.bottom + touchInset);
        layoutPresets();
        mHasTarget = true;
        invalidate();
    }

    private void layoutPresets() {
        float cx = mWidget.centerX();
        float centerY = mWidget.top;
        float totalW = mPresets.length * mPresetW + (mPresets.length - 1) * mPresetGap;
        float x = cx - totalW / 2f;
        float top = centerY - mPresetH / 2f;
        float bottom = centerY + mPresetH / 2f;
        for (RectF r : mPresets) {
            r.set(x, top, x + mPresetW, bottom);
            x += mPresetW + mPresetGap;
        }
    }

    private int hitPreset(float x, float y) {
        if (!mHasPresets) {
            return -1;
        }
        for (int i = 0; i < mPresets.length; i++) {
            if (mPresets[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    public void clearTarget() {
        if (mActiveEdges != 0) {
            finishResize(false);
        }
        if (mDownButton != 0) {
            requestParentDisallow(false);
        }
        mHasTarget = false;
        mActiveEdges = 0;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mDownButton = 0;
        mDownPreset = -1;
        mRemovePressed = false;
        if (mRevealAnim != null) {
            mRevealAnim.cancel();
            mRevealAnim = null;
        }
        mRevealAlpha = 1f;
        invalidate();
    }

    /**
     * Fade and lift the customization chrome in, matching Launcher3's
     * {@code AppWidgetResizeFrame.snapToWidget} handle fade (150ms).
     */
    public void revealChrome() {
        if (mRevealAnim != null) {
            mRevealAnim.cancel();
        }
        mRevealAlpha = 0f;
        mRevealAnim = ValueAnimator.ofFloat(0f, 1f);
        mRevealAnim.setDuration(150L);
        mRevealAnim.setInterpolator(Interpolators.DECELERATE_1_5);
        mRevealAnim.addUpdateListener(anim -> {
            mRevealAlpha = (float) anim.getAnimatedValue();
            invalidate();
        });
        mRevealAnim.start();
    }

    private int hitEdges(float x, float y) {
        if (x < mFrame.left - mBand || x > mFrame.right + mBand
                || y < mFrame.top - mBand || y > mFrame.bottom + mBand) {
            return 0;
        }
        int edges = 0;
        boolean nearLeft = (mResizableEdges & EDGE_LEFT) != 0
                && Math.abs(x - mFrame.left) <= mBand;
        boolean nearRight = (mResizableEdges & EDGE_RIGHT) != 0
                && Math.abs(x - mFrame.right) <= mBand;
        if (nearLeft && nearRight) {
            edges |= Math.abs(x - mFrame.left) <= Math.abs(x - mFrame.right)
                    ? EDGE_LEFT : EDGE_RIGHT;
        } else if (nearLeft) {
            edges |= EDGE_LEFT;
        } else if (nearRight) {
            edges |= EDGE_RIGHT;
        }
        boolean nearTop = (mResizableEdges & EDGE_TOP) != 0
                && Math.abs(y - mFrame.top) <= mBand;
        boolean nearBottom = (mResizableEdges & EDGE_BOTTOM) != 0
                && Math.abs(y - mFrame.bottom) <= mBand;
        if (nearTop && nearBottom) {
            edges |= Math.abs(y - mFrame.top) <= Math.abs(y - mFrame.bottom)
                    ? EDGE_TOP : EDGE_BOTTOM;
        } else if (nearTop) {
            edges |= EDGE_TOP;
        } else if (nearBottom) {
            edges |= EDGE_BOTTOM;
        }
        return edges;
    }

    private int hitButton(float x, float y) {
        int preset = hitPreset(x, y);
        if (preset >= 0) {
            return BUTTON_PRESET_BASE + preset;
        }
        if (mHasReconfigure && mBtnReconfigure.contains(x, y)) {
            return BUTTON_RECONFIGURE;
        }
        if (mBtnRemoveTouch.contains(x, y)) {
            return BUTTON_REMOVE;
        }
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mHasTarget || getVisibility() != VISIBLE) {
            return false;
        }
        int action = ev.getActionMasked();
        float x = ev.getX();
        float y = ev.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            int button = hitButton(x, y);
            if (button != 0) {
                mDownButton = button;
                mRemovePressed = button == BUTTON_REMOVE;
                invalidate();
                requestParentDisallow(true);
                return true;
            }
            int edges = hitEdges(x, y);
            if (edges != 0) {
                mActiveEdges = edges;
                mActivePointerId = ev.getPointerId(0);
                mDownX = ev.getX(0);
                mDownY = ev.getY(0);
                requestParentDisallow(true);
                if (mListener != null) {
                    mListener.onBorderDown(edges);
                }
                return true;
            }
            if (mWidget.contains(x, y)) {
                // Let the widget (or the card) handle taps inside the content area.
                return false;
            }
            if (mListener != null) {
                mListener.onTapOutside();
            }
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Keep the original finger in control of a resize. A second finger
            // must not turn into an accidental cancel/revert.
            return mActiveEdges != 0 || mDownButton != 0;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mActiveEdges != 0) {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    finishResize(false);
                    return true;
                }
                if (mListener != null) {
                    mListener.onBorderMove(mActiveEdges,
                            ev.getX(pointerIndex) - mDownX,
                            ev.getY(pointerIndex) - mDownY);
                }
                return true;
            }
            if (mDownButton != 0) {
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (mActiveEdges != 0) {
                finishResize(true);
                return true;
            }
            if (mDownButton != 0) {
                int button = mDownButton;
                mDownButton = 0;
                mRemovePressed = false;
                invalidate();
                requestParentDisallow(false);
                if (hitButton(x, y) == button && mListener != null) {
                    if (button >= BUTTON_PRESET_BASE) {
                        mListener.onPresetSelected(button - BUTTON_PRESET_BASE);
                    } else {
                        mListener.onButtonTap(button);
                    }
                }
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            boolean handled = mActiveEdges != 0 || mDownButton != 0;
            int liftedIndex = ev.getActionIndex();
            if (mActiveEdges != 0 && ev.getPointerId(liftedIndex) == mActivePointerId) {
                finishResize(false);
            }
            return handled;
        }
        // ACTION_CANCEL ends the gesture without applying a partial resize.
        if (mActiveEdges != 0) {
            finishResize(false);
            return true;
        }
        mDownButton = 0;
        mRemovePressed = false;
        invalidate();
        requestParentDisallow(false);
        return false;
    }

    private void finishResize(boolean commit) {
        int edges = mActiveEdges;
        mActiveEdges = 0;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        requestParentDisallow(false);
        if (mListener != null) {
            mListener.onBorderUp(edges, commit);
        }
    }

    private void requestParentDisallow(boolean disallow) {
        if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mHasTarget || getVisibility() != VISIBLE) {
            return;
        }
        int layer = -1;
        if (mRevealAlpha < 1f) {
            layer = canvas.saveLayerAlpha(0f, 0f, getWidth(), getHeight(),
                    (int) (255f * mRevealAlpha));
        }
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFramePaint);
        float cx = (mFrame.left + mFrame.right) / 2f;
        float cy = (mFrame.top + mFrame.bottom) / 2f;
        if ((mResizableEdges & EDGE_LEFT) != 0) {
            canvas.drawCircle(mFrame.left, cy, mHandleR, mHandlePaint);
        }
        if ((mResizableEdges & EDGE_RIGHT) != 0) {
            canvas.drawCircle(mFrame.right, cy, mHandleR, mHandlePaint);
        }
        if ((mResizableEdges & EDGE_TOP) != 0) {
            canvas.drawCircle(cx, mFrame.top, mHandleR, mHandlePaint);
        }
        if ((mResizableEdges & EDGE_BOTTOM) != 0) {
            canvas.drawCircle(cx, mFrame.bottom, mHandleR, mHandlePaint);
        }
        if (mHasReconfigure) {
            canvas.drawRoundRect(mBtnReconfigure, mBtnCorner, mBtnCorner, mBtnPaint);
            drawGear(canvas, mBtnReconfigure.centerX(), mBtnReconfigure.centerY());
        }
        float bx = mBtnRemove.centerX();
        float by = mBtnRemove.centerY();
        canvas.save();
        float revealScale = 0.84f + 0.16f * mRevealAlpha;
        float pressScale = mRemovePressed ? 0.88f : 1f;
        canvas.scale(revealScale * pressScale, revealScale * pressScale, bx, by);
        mRemovePaint.setColor(mAccent);
        canvas.drawCircle(bx, by, mRemoveSize / 2f, mRemovePaint);
        canvas.drawCircle(bx, by, mRemoveSize / 2f, mRemoveStrokePaint);
        float r = 4.5f * getResources().getDisplayMetrics().density;
        canvas.drawLine(bx - r, by - r, bx + r, by + r, mLinePaint);
        canvas.drawLine(bx - r, by + r, bx + r, by - r, mLinePaint);
        canvas.restore();
        if (mHasPresets) {
            drawPresets(canvas);
        }
        if (layer >= 0) {
            canvas.restoreToCount(layer);
        }
    }

    private void drawPresets(Canvas canvas) {
        float radius = mPresetH / 2f;
        for (int i = 0; i < mPresets.length; i++) {
            RectF rect = mPresets[i];
            canvas.drawRoundRect(rect, radius, radius, mPresetPaint);
            float ty = rect.centerY()
                    - (mPresetTextPaint.descent() + mPresetTextPaint.ascent()) / 2f;
            canvas.drawText(mPresetLabels[i], rect.centerX(), ty, mPresetTextPaint);
        }
    }

    private void drawGear(Canvas canvas, float cx, float cy) {
        float density = getResources().getDisplayMetrics().density;
        float toothW = 2.5f * density;
        float toothOuter = 11f * density;
        float toothInner = 6f * density;
        canvas.save();
        for (int i = 0; i < 8; i++) {
            canvas.rotate(45f, cx, cy);
            canvas.drawRect(cx - toothW / 2f, cy - toothOuter, cx + toothW / 2f,
                    cy - toothInner, mBtnIconPaint);
        }
        canvas.restore();
        canvas.drawCircle(cx, cy, 7f * density, mBtnIconPaint);
        canvas.drawCircle(cx, cy, 3f * density, mBtnHolePaint);
    }
}
