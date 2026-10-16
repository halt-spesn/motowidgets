package com.discoveryscreen.ui;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import com.discoveryscreen.R;
import com.discoveryscreen.widget.WidgetHostManager;
import com.discoveryscreen.widget.Interpolators;
import com.discoveryscreen.widget.RoundedCornerEnforcement;
import com.discoveryscreen.widget.WidgetCardView;
import com.discoveryscreen.widget.WidgetGridLayout;
import com.discoveryscreen.widget.WidgetHeightPolicy;
import com.discoveryscreen.widget.WidgetResizeFrame;
import com.discoveryscreen.widget.WidgetSpacing;
import com.discoveryscreen.widget.ReloadGate;
import com.discoveryscreen.widget.StagedQueue;
import com.discoveryscreen.widget.WidgetInflationPolicy;
import com.discoveryscreen.widget.WidgetReloadPolicy;
import com.discoveryscreen.widget.WidgetSizing;
import com.discoveryscreen.widget.WidgetStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WidgetShelfView extends FrameLayout {
    private static final String TAG = "WidgetShelfView";

    public interface SwipeProgressListener {
        void onProgress(float progress);
    }

    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;
    private ViewGroup mWidgetsContainer;
    private android.view.ViewTreeObserver.OnPreDrawListener mReflowListener;
    private View mPlaceholderCard;
    private View mShelfContentRoot;
    private Button mBtnEdit;
    private View mAddWidgetButton;
    private ScrollView mShelfScrollView;
    private int mPendingScrollRestoreY = -1;
    private WidgetPickerSheet mWidgetPickerSheet;
    private boolean mIsEditMode = false;
    private Runnable mOnCloseListener;
    private Runnable mOnWidgetAddListener;
    private int mPendingEntranceWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private SwipeProgressListener mSwipeProgressListener;

    private float mDownX;
    private float mDownY;
    private boolean mIsHorizontalSwipe = false;
    private int mTouchSlop;
    private boolean mIsRtl = false;
    private boolean mIsLightStatusBar = false;
    private boolean mIsLightNavBar = false;
    private List<Integer> mLastInflatedIds = null;
    private boolean mLastInflatedEditMode = false;
    private long mLastReloadMs = -1L;
    private final AtomicInteger mInflationGen = new AtomicInteger();
    // A request token discards stale provider-query results without cancelling
    // an already staged UI reload unless the newer request actually commits.
    private final AtomicInteger mReloadRequestGen = new AtomicInteger();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final StagedQueue mStagedQueue = new StagedQueue();
    private boolean mPumpScheduled = false;
    private static final long PUMP_FALLBACK_MS = 50L;
    private final Choreographer.FrameCallback mFrameCallback = frameTimeNanos -> pumpOnce();
    private final Runnable mPumpFallback = this::pumpOnce;
    private final java.util.Map<Integer, View> mCardCache = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> mCardCacheHeight = new java.util.HashMap<>();
    private final java.util.Map<Integer, Boolean> mCardCacheEditMode = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> mCardCachePos = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mWidgetCostMs = new java.util.HashMap<>();
    private final java.util.IdentityHashMap<WidgetCardView, android.animation.ValueAnimator>
            mGridMoveAnimators = new java.util.IdentityHashMap<>();
    private java.util.Map<Integer, float[]> mPendingReorderOrigins;
    private android.view.ViewTreeObserver.OnPreDrawListener mReorderPreDrawListener;
    private final ReloadGate.Gate mReloadGate = new ReloadGate.Gate();
    private final Object mPolicyLock = new Object();
    private static final ExecutorService sLoader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shelf-loader");
        t.setDaemon(true);
        return t;
    });
    private final java.util.Set<Integer> mFailedWidgetIds = new java.util.HashSet<>();
    private final java.util.Set<Integer> mWidgetsBeingRemoved = new java.util.HashSet<>();
    private boolean mDeferSlowWidgets = false;
    private WidgetResizeFrame mResizeFrame;
    private WidgetCardView mSelectedCard;
    private int mResizeStartW;
    private int mResizeStartH;
    private float mResizeStartLeft;
    private float mResizeStartTop;
    private float mResizeStartTranslationX;
    private float mResizeStartTranslationY;
    private int mResizeStartCol;
    private int mResizeStartSpan;
    private int mResizeMinW = 1;
    private int mResizeMaxW;
    private int mResizeMinH = 1;
    private int mResizeMaxH;
    private boolean mCardGestureActive = false;
    private boolean mPendingReload = false;
    private boolean mPendingReloadForce = false;
    private boolean mNeedsReloadOnNextOpen = false;

    public WidgetShelfView(Context context) {
        super(context);
        init(context);
    }

    public WidgetShelfView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WidgetShelfView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setOnCloseListener(Runnable listener) {
        this.mOnCloseListener = listener;
    }

    public void setOnWidgetAddListener(Runnable listener) {
        this.mOnWidgetAddListener = listener;
    }

    public void reloadWidgetsWithEntrance(int widgetId) {
        mPendingEntranceWidgetId = widgetId;
        reloadWidgets(true);
    }

    public void restorePickerAfterWidgetAdd(int widgetId) {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            mPendingEntranceWidgetId = widgetId;
        }
        reloadWidgets(true);
        if (mWidgetPickerSheet != null) {
            mWidgetPickerSheet.showAnimated();
            mWidgetPickerSheet.bringToFront();
        }
    }

    public void prepareForShelfClose() {
        persistGridState();
        boolean wasEditing = mIsEditMode;
        mIsEditMode = false;
        if (mBtnEdit != null) mBtnEdit.setText("Edit");
        if (wasEditing) mNeedsReloadOnNextOpen = true;
        if (mWidgetPickerSheet != null
                && mWidgetPickerSheet.getVisibility() == View.VISIBLE) {
            mWidgetPickerSheet.dismiss();
        }
    }

    public void onShelfOpened() {
        if (!mNeedsReloadOnNextOpen) return;
        mNeedsReloadOnNextOpen = false;
        reloadWidgets(true);
    }

    public void setSwipeProgressListener(SwipeProgressListener listener) {
        this.mSwipeProgressListener = listener;
    }

    public void setIsRtl(boolean isRtl) {
        this.mIsRtl = isRtl;
    }

    public boolean hasInflated() {
        synchronized (mPolicyLock) {
            return mLastInflatedIds != null;
        }
    }

    public void setDeferSlowWidgets(boolean defer) {
        mDeferSlowWidgets = defer;
        if (!defer) schedulePump();
    }

    public void setSwipeActive(boolean active) {
        if (active) mReloadGate.onSwipeStart();
    }

    public void settleSwipe(boolean opened) {
        ReloadGate.SettleAction action = mReloadGate.onSettled(opened);
        if (action == ReloadGate.SettleAction.FLUSH_NORMAL) {
            reloadWidgets(false);
        } else if (action == ReloadGate.SettleAction.FLUSH_FORCE) {
            reloadWidgets(true);
        }
        schedulePump();
    }

    public void dropPendingReload() {
        mReloadGate.reset();
        schedulePump();
    }

    public void cancelPendingInflation() {
        mReloadRequestGen.incrementAndGet();
        mInflationGen.incrementAndGet();
        mStagedQueue.clear();
        mPumpScheduled = false;
        mMainHandler.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        mReloadGate.reset();
        mCardGestureActive = false;
        mPendingReload = false;
        mPendingReloadForce = false;
    }

    public boolean isRtl() {
        return mIsRtl;
    }

    private void pumpOnce() {
        if (!mPumpScheduled) return;
        mPumpScheduled = false;
        mMainHandler.removeCallbacks(mPumpFallback);
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);

        Runnable next = mStagedQueue.takeReady(mInflationGen.get(),
                costMs -> WidgetInflationPolicy.shouldDefer(isGestureBlocking(), costMs));
        if (next == null) return;
        next.run();
        schedulePump();
    }

    private void schedulePump() {
        if (mPumpScheduled || mStagedQueue.isEmpty()) return;
        mPumpScheduled = true;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
        mMainHandler.postDelayed(mPumpFallback, PUMP_FALLBACK_MS);
    }

    private boolean isGestureBlocking() {
        return mDeferSlowWidgets || mReloadGate.isSwipeActive();
    }

    public void setLightSystemBars(boolean lightStatus, boolean lightNav) {
        this.mIsLightStatusBar = lightStatus;
        this.mIsLightNavBar = lightNav;
        applyMonetColors(getContext());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (mWidgetPickerSheet != null && mWidgetPickerSheet.getVisibility() == View.VISIBLE) {
                mWidgetPickerSheet.dismiss();
                return true;
            }
            if (mOnCloseListener != null) {
                mOnCloseListener.run();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                mIsHorizontalSwipe = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - mDownX;
                float dy = ev.getRawY() - mDownY;
                boolean isDismissDirection = mIsRtl ? (dx > mTouchSlop) : (dx < -mTouchSlop);
                if (isDismissDirection && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    mIsHorizontalSwipe = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsHorizontalSwipe = false;
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mIsHorizontalSwipe && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - mDownX;
                boolean isDismissDrag = mIsRtl ? (dx > 0) : (dx < 0);
                if (isDismissDrag) {
                    setTranslationX(dx);
                    int w = getWidth() > 0 ? getWidth() : 1080;
                    float dismissDx = mIsRtl ? -dx : dx;
                    float progress = Math.max(0.0f, 1.0f + dismissDx / w);
                    setAlpha(progress);
                    if (mSwipeProgressListener != null) {
                        mSwipeProgressListener.onProgress(progress);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float totalDx = ev.getRawX() - mDownX;
                int width = getWidth() > 0 ? getWidth() : 1080;
                boolean flungToClose = mIsRtl
                        ? (totalDx > width * 0.15f)
                        : (totalDx < -width * 0.15f);
                if (flungToClose) {
                    if (mOnCloseListener != null) {
                        mOnCloseListener.run();
                    }
                } else {
                    animate().translationX(0).alpha(1.0f).setDuration(160).setUpdateListener(animation -> {
                        if (mSwipeProgressListener != null) {
                            int w = getWidth() > 0 ? getWidth() : 1080;
                            float tx = getTranslationX();
                            float dismiss = mIsRtl ? -tx : tx;
                            float progress = Math.max(0.0f, 1.0f + dismiss / w);
                            mSwipeProgressListener.onProgress(progress);
                        }
                    }).start();
                }
                mIsHorizontalSwipe = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void init(Context context) {
        setFocusable(true);
        setFocusableInTouchMode(true);
        // Ask WindowManager to keep this full-screen overlay laid out behind
        // the navigation bar. The picker can then paint its own surface into
        // that area while its content remains inset by WidgetPickerSheet.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAppWidgetManager = AppWidgetManager.getInstance(context);
        mAppWidgetHost = WidgetHostManager.getHost(context);

        View root = LayoutInflater.from(context).inflate(
            context.getResources().getIdentifier("widget_shelf_overlay", "layout", context.getPackageName()),
            this, true
        );
        int shelfRootId = context.getResources().getIdentifier(
                "shelf_root", "id", context.getPackageName());
        mShelfContentRoot = root.findViewById(shelfRootId);

        // Remove scrolling indicator
        int scrollId = context.getResources().getIdentifier("shelf_scroll", "id", context.getPackageName());
        ScrollView scrollView = root.findViewById(scrollId);
        mShelfScrollView = scrollView;
        if (scrollView != null) {
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.getViewTreeObserver().addOnScrollChangedListener(
                    this::repositionResizeFrame);
        }

        int containerId = context.getResources().getIdentifier("shelf_widgets_container", "id", context.getPackageName());
        int placeholderId = context.getResources().getIdentifier("shelf_placeholder", "id", context.getPackageName());
        int btnPlaceholderAddId = context.getResources().getIdentifier("btn_placeholder_add", "id", context.getPackageName());
        int btnEditId = context.getResources().getIdentifier("btn_shelf_edit", "id", context.getPackageName());

        mWidgetsContainer = root.findViewById(containerId);
        mPlaceholderCard = root.findViewById(placeholderId);
        mBtnEdit = root.findViewById(btnEditId);
        adjustScrollPaddingForCutout(root);
        Button btnPlaceholderAdd = root.findViewById(btnPlaceholderAddId);
        View.OnClickListener addListener = v -> showWidgetPicker();

        if (btnPlaceholderAdd != null) btnPlaceholderAdd.setOnClickListener(addListener);

        if (mBtnEdit != null) {
            mBtnEdit.setOnClickListener(v -> {
                if (gridCards().isEmpty()) {
                    showWidgetPicker();
                    return;
                }
                mIsEditMode = !mIsEditMode;
                animateEditButtonLabel(mIsEditMode ? "Done" : "Edit");
                reloadWidgets(true);
            });
        }

        if (mWidgetsContainer != null) {
            mWidgetsContainer.setLongClickable(true);
            mWidgetsContainer.setOnLongClickListener(v -> {
                showWidgetPicker();
                return true;
            });
        }
        if (scrollView != null) {
            scrollView.setLongClickable(true);
            scrollView.setOnLongClickListener(v -> {
                showWidgetPicker();
                return true;
            });
        }

        mResizeFrame = new WidgetResizeFrame(context);
        mResizeFrame.setVisibility(View.GONE);
        mResizeFrame.setAccentColor(MonetColors.getAccentColor(context));
        mResizeFrame.setListener(mFrameListener);
        addView(mResizeFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        applyMonetColors(context);
        reloadWidgets();
    }

    private void adjustScrollPaddingForCutout(View root) {
        int scrollId = getResources().getIdentifier("shelf_scroll", "id", getContext().getPackageName());
        View scrollView = root.findViewById(scrollId);
        int headerId = getResources().getIdentifier("shelf_header", "id", getContext().getPackageName());
        int statusBarHeight = getStatusBarHeight();
        int headerTop = Math.max(statusBarHeight + dpToPx(getContext(), 8),
                dpToPx(getContext(), 16));
        View header = root.findViewById(headerId);
        if (header != null && header.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) header.getLayoutParams();
            lp.topMargin = headerTop;
            header.setLayoutParams(lp);
        }
        if (scrollView != null) {
            int padTop = headerTop + dpToPx(getContext(), 68);
            scrollView.setPadding(
                scrollView.getPaddingLeft(),
                padTop,
                scrollView.getPaddingRight(),
                scrollView.getPaddingBottom()
            );
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            result = getResources().getDimensionPixelSize(resId);
        }
        return result;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyMonetColors(Context context) {
        int accentColor = MonetColors.getAccentColor(context);
        if (mBtnEdit != null) {
            mBtnEdit.setTextColor(accentColor);
        }
        if (mResizeFrame != null) {
            mResizeFrame.setAccentColor(accentColor);
            mResizeFrame.setCornerRadius(
                    RoundedCornerEnforcement.computeEnforcedRadius(context));
        }
        if (mWidgetsContainer instanceof WidgetGridLayout) {
            ((WidgetGridLayout) mWidgetsContainer).setAccentColor(accentColor);
        }
    }

    public void startListening() {
        WidgetHostManager.ensureListening(getContext());
    }

    public void stopListening() {
        // Shared host manages listening
    }

    public void reloadWidgets() {
        reloadWidgets(false);
    }

    public void reloadWidgets(boolean force) {
        if (mCardGestureActive) {
            // A drag/resize owns the container right now; a reload would detach
            // the card under the finger. Defer and flush when the gesture ends.
            mPendingReload = true;
            mPendingReloadForce |= force;
            Log.d(TAG, "reloadWidgets: deferred (card gesture)");
            return;
        }
        if (mReloadGate.onReloadRequest(force)
                == ReloadGate.RequestDecision.DEFERRED) {
            Log.d(TAG, "reloadWidgets: deferred (swipe active)");
            return;
        }
        final Context context = getContext();
        final boolean editMode = mIsEditMode;
        final int requestGen = mReloadRequestGen.incrementAndGet();
        sLoader.execute(() -> {
            if (requestGen != mReloadRequestGen.get()) return;
            List<Integer> storedIds = WidgetStorage.getWidgetIds(context);
            Set<Integer> alive = new HashSet<>();
            Map<Integer, AppWidgetProviderInfo> infoById = new HashMap<>();
            for (int id : storedIds) {
                try {
                    AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(id);
                    if (info != null) {
                        alive.add(id);
                        infoById.put(id, info);
                    }
                } catch (Exception ignored) {}
            }
            if (!storedIds.isEmpty() && alive.isEmpty()) {
                // A null provider lookup for every saved ID can mean the host
                // manager is temporarily unavailable during restore/startup.
                // Do not prune the saved shelf or replace its current UI yet.
                Log.w(TAG, "reloadWidgets: all saved widget lookups failed; keeping shelf state");
                return;
            }
            List<Integer> widgetIds = WidgetStorage.pruneStaleWidgets(context, storedIds, alive);
            ensurePlacements(context, widgetIds);
            Map<Integer, Integer> heightById = new HashMap<>();
            for (int id : widgetIds) {
                heightById.put(id, WidgetStorage.getWidgetHeightDp(context, id));
            }

            if (requestGen != mReloadRequestGen.get()) return;
            mMainHandler.post(() -> {
                if (requestGen != mReloadRequestGen.get()) return;
                long nowMs = SystemClock.uptimeMillis();
                synchronized (mPolicyLock) {
                    boolean idsEqual = WidgetReloadPolicy.idsEqual(widgetIds, mLastInflatedIds);
                    boolean editChanged = (editMode != mLastInflatedEditMode);
                    boolean skip = !WidgetReloadPolicy.shouldReload(
                            force, idsEqual, editChanged, nowMs, mLastReloadMs)
                        || (!force && idsEqual && !editChanged && mLastInflatedIds != null
                            && mFailedWidgetIds.isEmpty());
                    if (skip) {
                        Log.d(TAG, "reloadWidgets: skip n=" + widgetIds.size());
                        return;
                    }
                }
                // Only invalidate staged work after this request is known to
                // require a reload. A no-op query must not cancel a good render.
                int gen = mInflationGen.incrementAndGet();
                continueReloadOnUi(context, widgetIds, infoById, heightById, editMode, gen);
            });
        });
    }

    private void continueReloadOnUi(Context context, List<Integer> widgetIds,
                                    Map<Integer, AppWidgetProviderInfo> infoById,
                                    Map<Integer, Integer> heightById,
                                    boolean editMode, int gen) {

        final boolean editModeTransition;
        synchronized (mPolicyLock) {
            editModeTransition = editMode != mLastInflatedEditMode;
        }

        if (mPendingEntranceWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && !widgetIds.contains(mPendingEntranceWidgetId)) {
            mPendingEntranceWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        }

        synchronized (mPolicyLock) {
            mFailedWidgetIds.clear();
        }

        mStagedQueue.offer(gen, -1L, () -> {
            prepareContainerForReload(context, widgetIds);
            if (widgetIds.isEmpty()) {
                mIsEditMode = false;
                if (mPlaceholderCard != null) mPlaceholderCard.setVisibility(View.VISIBLE);
                if (mBtnEdit != null) {
                    mBtnEdit.setVisibility(View.VISIBLE);
                    mBtnEdit.setText("Add");
                }
            } else {
                if (mPlaceholderCard != null) mPlaceholderCard.setVisibility(View.GONE);
                if (mBtnEdit != null) {
                    mBtnEdit.setVisibility(View.VISIBLE);
                    if (!editModeTransition) {
                        mBtnEdit.setText(mIsEditMode ? "Done" : "Edit");
                    }
                }
                if (editMode) addAddWidgetButton(context, editModeTransition);
            }
        });

        final int[] nextPos = {0};
        final int total = widgetIds.size();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidthDp = Math.max(280, (int) (dm.widthPixels / dm.density) - 32);

        for (int i = 0; i < widgetIds.size(); i++) {
            final int pos = i;
            final int widgetId = widgetIds.get(i);
            final AppWidgetProviderInfo info = infoById.get(widgetId);
            if (info == null) {
                synchronized (mPolicyLock) {
                    mFailedWidgetIds.add(widgetId);
                }
                continue;
            }

            Integer prefetchedHeight = heightById.get(widgetId);
            int heightDp = prefetchedHeight != null ? prefetchedHeight : 0;
            final AppWidgetProviderInfo fInfo = info;
            final int fHeightDp = heightDp;
            final int fPos = pos;
            final boolean fEditMode = editMode;
            final boolean fEditModeTransition = editModeTransition;
            Long lastCost = mWidgetCostMs.get(widgetId);
            final long fCostMs = lastCost != null ? lastCost : -1L;
            mStagedQueue.offer(gen, fCostMs, () -> {
                long w0 = SystemClock.uptimeMillis();
                View cached = mCardCache.get(widgetId);
                Integer cachedH = mCardCacheHeight.get(widgetId);
                Boolean cachedE = mCardCacheEditMode.get(widgetId);
                Integer cachedP = mCardCachePos.get(widgetId);
                if (cached != null && cached.getParent() == null
                        && cachedH != null && cachedE != null && cachedP != null
                        && WidgetInflationPolicy.cacheValid(
                                cachedH, cachedE, cachedP, fHeightDp, fEditMode, fPos)) {
                    try {
                        if (cached instanceof WidgetCardView) {
                            // The placement may have changed (reorder or overlap
                            // resolution) since the card was cached; resync it.
                            WidgetCardView cachedCard = (WidgetCardView) cached;
                            int[] p = WidgetStorage.getPlacement(context, widgetId);
                            if (p != null) {
                                cachedCard.setGridPos(p[0], p[1], p[2]);
                                layoutCardFromPlacement(cachedCard);
                            }
                        }
                        int at = Math.min(nextPos[0], mWidgetsContainer.getChildCount());
                        boolean modeEntrance = fEditModeTransition
                                && widgetId != mPendingEntranceWidgetId;
                        if (modeEntrance) prepareEditModeEntrance(cached);
                        else prepareWidgetEntrance(cached, widgetId);
                        addCardChild(cached, at);
                        if (modeEntrance) startEditModeEntrance(cached, fPos);
                        else startWidgetEntrance(cached, widgetId);
                        nextPos[0]++;
                        if (nextPos[0] == 1 && mPlaceholderCard != null) {
                            mPlaceholderCard.setVisibility(View.GONE);
                        }
                        Log.d(TAG, "widget id=" + widgetId + " reused ms="
                                + (SystemClock.uptimeMillis() - w0));
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "Cache reuse failed for " + widgetId + ", rebuilding", e);
                        invalidateCachedCard(widgetId);
                    }
                }
                try {
                    View card = buildWidgetCard(context, widgetId, fInfo, fPos, total,
                            fHeightDp, screenWidthDp, fEditMode);
                    if (card == null) {
                        synchronized (mPolicyLock) {
                            mFailedWidgetIds.add(widgetId);
                        }
                    }

                    if (card != null) {
                        mCardCache.put(widgetId, card);
                        mCardCacheHeight.put(widgetId, fHeightDp);
                        mCardCacheEditMode.put(widgetId, fEditMode);
                        mCardCachePos.put(widgetId, fPos);
                        int at = Math.min(nextPos[0], mWidgetsContainer.getChildCount());
                        boolean modeEntrance = fEditModeTransition
                                && widgetId != mPendingEntranceWidgetId;
                        if (modeEntrance) prepareEditModeEntrance(card);
                        else prepareWidgetEntrance(card, widgetId);
                        addCardChild(card, at);
                        if (modeEntrance) startEditModeEntrance(card, fPos);
                        else startWidgetEntrance(card, widgetId);
                        nextPos[0]++;
                        if (nextPos[0] == 1 && mPlaceholderCard != null) {
                            mPlaceholderCard.setVisibility(View.GONE);
                        }
                    }
                    long cost = SystemClock.uptimeMillis() - w0;
                    mWidgetCostMs.put(widgetId, cost);
                    Log.d(TAG, "widget id=" + widgetId + " ms=" + cost);
                } catch (Exception e) {
                    synchronized (mPolicyLock) {
                        mFailedWidgetIds.add(widgetId);
                    }
                    Log.e(TAG, "Failed to inflate widget: " + widgetId, e);
                }
            });
        }

        if (editModeTransition) {
            mStagedQueue.offer(gen, -1L, this::animateEditModeFooter);
        }
        if (mPendingScrollRestoreY >= 0) {
            mStagedQueue.offer(gen, -1L, this::restorePendingScrollPosition);
        }
        if (mPendingReorderOrigins != null) {
            mStagedQueue.offer(gen, -1L, this::animatePendingReorder);
        }

        synchronized (mPolicyLock) {
            mLastInflatedIds = new ArrayList<>(widgetIds);
            mLastInflatedEditMode = editMode;
            mLastReloadMs = android.os.SystemClock.uptimeMillis();
        }
        Log.d(TAG, "reloadWidgets: staged n=" + widgetIds.size()
                + " edit=" + editMode);
        schedulePump();
    }

    private void prepareContainerForReload(Context context, List<Integer> widgetIds) {
        deselectCard();
        if (mAddWidgetButton != null && mAddWidgetButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) mAddWidgetButton.getParent()).removeView(mAddWidgetButton);
        }
        mAddWidgetButton = null;
        for (Integer cachedId : new ArrayList<>(mCardCache.keySet())) {
            if (!widgetIds.contains(cachedId)) {
                invalidateCachedCard(cachedId);
                mWidgetCostMs.remove(cachedId);
            }
        }

        applyMonetColors(context);

        // Remove only dynamic widget views, keep placeholder and edit button
        for (int i = mWidgetsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = mWidgetsContainer.getChildAt(i);
            if (child != mPlaceholderCard && child != mBtnEdit) {
                mWidgetsContainer.removeViewAt(i);
            }
        }
    }

    private void invalidateCachedCard(int widgetId) {
        mCardCache.remove(widgetId);
        mCardCacheHeight.remove(widgetId);
        mCardCacheEditMode.remove(widgetId);
        mCardCachePos.remove(widgetId);
    }

    private void addAddWidgetButton(Context context, boolean animateEntrance) {
        Button btnAddMore = new Button(context);
        btnAddMore.setText("+ Add widgets");
        btnAddMore.setTextColor(MonetColors.getAccentColor(context));
        btnAddMore.setTextSize(14);
        btnAddMore.setAllCaps(false);
        btnAddMore.setGravity(Gravity.CENTER);
        btnAddMore.setBackgroundColor(Color.TRANSPARENT);
        btnAddMore.setMinWidth(0);
        btnAddMore.setPadding(dpToPx(context, 8), 0, dpToPx(context, 8), 0);
        btnAddMore.setContentDescription("Add widgets");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(context, 48));
        btnAddMore.setLayoutParams(addLp);
        btnAddMore.setOnClickListener(v -> showWidgetPicker());
        if (animateEntrance) {
            btnAddMore.setAlpha(0f);
            btnAddMore.setScaleX(0.96f);
            btnAddMore.setScaleY(0.96f);
            btnAddMore.setTranslationY(dpToPx(context, 5));
        }
        mAddWidgetButton = btnAddMore;
        if (mBtnEdit != null && mBtnEdit.getParent() instanceof LinearLayout) {
            LinearLayout header = (LinearLayout) mBtnEdit.getParent();
            header.addView(btnAddMore, header.indexOfChild(mBtnEdit), addLp);
        }
    }

    private void animateEditButtonLabel(String label) {
        Button button = mBtnEdit;
        if (button == null) return;
        button.animate().cancel();
        button.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(90L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (button != mBtnEdit) return;
                    button.setText(label);
                    button.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(210L)
                            .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                            .start();
                })
                .start();
    }

    private void prepareEditModeEntrance(View card) {
        card.animate().cancel();
        card.setAlpha(0f);
        card.setScaleX(0.98f);
        card.setScaleY(0.98f);
        // Entering edit mode lifts the cards into their editing controls;
        // leaving edit mode settles them back down. Using opposite, small
        // offsets makes the two states read as one page transition.
        card.setTranslationY(dpToPx(getContext(), mIsEditMode ? 6 : -6));
    }

    private void startEditModeEntrance(View card, int position) {
        card.postOnAnimation(() -> {
            if (!card.isAttachedToWindow()) return;
            card.animate().cancel();
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(84L, Math.max(0, position) * 18L))
                    .setDuration(260L)
                    .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                    .start();
        });
    }

    private void animateEditModeFooter() {
        View button = mAddWidgetButton;
        if (button == null || button.getParent() == null) return;
        button.animate().cancel();
        button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(18L)
                .setDuration(240L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .start();
    }

    private void showWidgetPicker() {
        if (mWidgetPickerSheet != null) {
            setPickerBackgroundBlur(true);
            mWidgetPickerSheet.showAnimated();
            mWidgetPickerSheet.bringToFront();
            mWidgetPickerSheet.requestFocus();
            return;
        }
        mWidgetPickerSheet = new WidgetPickerSheet(getContext(), new WidgetPickerSheet.Listener() {
            @Override
            public void onWidgetSelected(android.content.ComponentName provider) {
                if (mOnWidgetAddListener != null) mOnWidgetAddListener.run();
                Intent addIntent = new Intent(getContext(), WidgetAddActivity.class);
                addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                addIntent.putExtra(WidgetPickerSheet.EXTRA_PROVIDER, provider);
                android.os.Bundle options = ActivityOptions.makeCustomAnimation(
                        getContext(), 0, 0).toBundle();
                getContext().startActivity(addIntent, options);
            }

            @Override
            public void onDismissed() {
                setPickerBackgroundBlur(false);
                WidgetPickerSheet sheet = mWidgetPickerSheet;
                mWidgetPickerSheet = null;
                if (sheet != null) removeView(sheet);
            }
        });
        addView(mWidgetPickerSheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setPickerBackgroundBlur(true);
        mWidgetPickerSheet.bringToFront();
        mWidgetPickerSheet.showAnimated();
        mWidgetPickerSheet.requestFocus();
    }

    /** Blur only the shelf layer; the picker is a sibling and stays crisp above it. */
    private void setPickerBackgroundBlur(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        RenderEffect effect = enabled
                ? RenderEffect.createBlurEffect(dpToPx(getContext(), 20),
                        dpToPx(getContext(), 20), Shader.TileMode.CLAMP)
                : null;
        if (mShelfContentRoot != null) mShelfContentRoot.setRenderEffect(effect);
        if (mResizeFrame != null) mResizeFrame.setRenderEffect(effect);
    }

    public void onCrossWindowBlurChanged(boolean enabled) {
        if (mWidgetPickerSheet != null) {
            mWidgetPickerSheet.onCrossWindowBlurChanged(enabled);
        }
    }

    private View buildWidgetCard(Context context, int widgetId, AppWidgetProviderInfo info,
                                 int pos, int total, int heightDp,
                                 int screenWidthDp, boolean editMode) {
        WidgetCardView card = new WidgetCardView(context);
        card.setWidgetId(widgetId);
        card.setTapMotionEnabled(!editMode);
        card.setDelegate(mCardDelegate);
        card.setPadding(0, 0, 0, 0);

        int[] placement = WidgetStorage.getPlacement(context, widgetId);
        int gridRow = 0;
        int gridCol = 0;
        int gridSpan = WidgetGridLayout.GRID_COLUMNS;
        if (placement != null) {
            gridRow = placement[0];
            gridCol = placement[1];
            gridSpan = placement[2];
        }
        card.setGridPos(gridRow, gridCol, gridSpan);
        int cardWidth = gridSpan >= WidgetGridLayout.GRID_COLUMNS
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 0);
        card.setLayoutParams(cardParams);

        if (editMode) {
            LinearLayout editBar = new LinearLayout(context);
            editBar.setOrientation(LinearLayout.HORIZONTAL);
            editBar.setGravity(Gravity.CENTER_VERTICAL);
            editBar.setBackground(createFrostedSurface(context));
            editBar.setElevation(dpToPx(context, 2));
            editBar.setPadding(dpToPx(context, 5), 0, dpToPx(context, 5), 0);

            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 44)
            );
            barParams.setMargins(0, 0, 0, dpToPx(context, 10));
            editBar.setLayoutParams(barParams);

            if (gridSpan == 1) {
                View more = editIconAction(context,
                        new int[]{R.drawable.ic_widget_action_more}, "Widget actions",
                        Color.WHITE, Color.TRANSPARENT);
                editBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                more.setOnClickListener(v -> showCompactWidgetActions(v, pos, total, widgetId));
                editBar.addView(more, new LinearLayout.LayoutParams(dpToPx(context, 36),
                        dpToPx(context, 36)));
            } else if (gridSpan == 2) {
                int moveAccent = MonetColors.getAccentColor(context);
                View move = editIconAction(context,
                        new int[]{R.drawable.ic_widget_action_up, R.drawable.ic_widget_action_down},
                        "Move widget", moveAccent, withAlpha(moveAccent, 0x24));
                move.setOnClickListener(v -> showMoveWidgetMenu(v,
                        WidgetStorage.getWidgetIds(context).indexOf(widgetId),
                        WidgetStorage.getWidgetIds(context).size()));
                LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(
                        dpToPx(context, 36), dpToPx(context, 36));
                moveParams.rightMargin = dpToPx(context, 4);
                editBar.addView(move, moveParams);
                editBar.addView(sizeAction(context, heightDp, widgetId),
                        new LinearLayout.LayoutParams(0, dpToPx(context, 36), 1f));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        dpToPx(context, 36), dpToPx(context, 36));
                removeParams.leftMargin = dpToPx(context, 4);
                editBar.addView(removeAction(context, widgetId), removeParams);
            } else {
                int moveAccent = MonetColors.getAccentColor(context);
                View btnUp = editIconAction(context,
                        new int[]{R.drawable.ic_widget_action_up}, "Move widget up",
                        pos > 0 ? moveAccent : withAlpha(moveAccent, 0x80),
                        withAlpha(moveAccent, pos > 0 ? 0x24 : 0x0C));
                btnUp.setEnabled(pos > 0);
                btnUp.setOnClickListener(v -> moveWidget(pos, pos - 1));
                LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(
                        dpToPx(context, 36), dpToPx(context, 36));
                upParams.rightMargin = dpToPx(context, 4);
                editBar.addView(btnUp, upParams);

                View btnDown = editIconAction(context,
                        new int[]{R.drawable.ic_widget_action_down}, "Move widget down",
                        pos < total - 1 ? moveAccent : withAlpha(moveAccent, 0x80),
                        withAlpha(moveAccent, pos < total - 1 ? 0x24 : 0x0C));
                btnDown.setEnabled(pos < total - 1);
                btnDown.setOnClickListener(v -> moveWidget(pos, pos + 1));
                LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(
                        dpToPx(context, 36), dpToPx(context, 36));
                downParams.rightMargin = dpToPx(context, 4);
                editBar.addView(btnDown, downParams);

                View divider = new View(context);
                divider.setBackgroundColor(0x24FFFFFF);
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        dpToPx(context, 1), dpToPx(context, 20));
                dividerLp.setMargins(dpToPx(context, 4), 0, dpToPx(context, 8), 0);
                editBar.addView(divider, dividerLp);
                editBar.addView(sizeAction(context, heightDp, widgetId),
                        new LinearLayout.LayoutParams(0, dpToPx(context, 36), 1f));
                LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                        dpToPx(context, 36), dpToPx(context, 36));
                deleteLp.leftMargin = dpToPx(context, 4);
                editBar.addView(removeAction(context, widgetId), deleteLp);
            }

            card.addView(editBar);
        }

        try {
            long c0 = SystemClock.uptimeMillis();
            AppWidgetHostView hostView = mAppWidgetHost.createView(context, widgetId, info);
            long c1 = SystemClock.uptimeMillis();

            int effectiveHeightDp = heightDp > 0 ? heightDp : (info.minHeight > 0 ? info.minHeight : 150);
            int effectiveWidthDp = Math.round(spanWidthPx(context, gridSpan, screenWidthDp)
                    / context.getResources().getDisplayMetrics().density);
            try {
                WidgetSizing.updateHostViewSize(context, hostView, info,
                        effectiveWidthDp, effectiveHeightDp);
            } catch (Exception ignored) {}
            Log.d(TAG, "widget id=" + widgetId + " create=" + (c1 - c0)
                    + " size=" + (SystemClock.uptimeMillis() - c1));

            if (heightDp > 0) {
                int heightPx = dpToPx(context, heightDp);
                LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    heightPx
                );
                FrameLayout widgetFrame = new FrameLayout(context);
                widgetFrame.addView(hostView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (editMode) addWidgetInteractionShield(context, widgetFrame);
                card.addView(widgetFrame, hostParams);
            } else {
                LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                FrameLayout widgetFrame = new FrameLayout(context);
                widgetFrame.addView(hostView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                if (editMode) addWidgetInteractionShield(context, widgetFrame);
                card.addView(widgetFrame, hostParams);
            }
            card.bindResizeTarget(hostView);

            return card;
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate widget: " + widgetId, e);
            return null;
        }
    }

    private void addWidgetInteractionShield(Context context, FrameLayout widgetFrame) {
        View shield = new View(context);
        shield.setClickable(true);
        shield.setFocusable(true);
        shield.setContentDescription("Widget interaction disabled while editing");
        shield.setOnClickListener(v -> { });
        widgetFrame.addView(shield, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View editIconAction(Context context, int[] iconIds, String description,
                                int tint, int fillColor) {
        FrameLayout action = new FrameLayout(context);
        action.setContentDescription(description);
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(fillColor);
        fill.setCornerRadius(dpToPx(context, 12));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dpToPx(context, 12));
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), fill, mask));

        LinearLayout icons = new LinearLayout(context);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        icons.setGravity(Gravity.CENTER);
        for (int iconId : iconIds) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconId);
            icon.setImageTintList(ColorStateList.valueOf(tint));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dpToPx(context, iconIds.length == 1 ? 20 : 14),
                    dpToPx(context, iconIds.length == 1 ? 20 : 14));
            if (iconIds.length > 1 && icons.getChildCount() > 0) {
                iconParams.leftMargin = dpToPx(context, 3);
            }
            icons.addView(icon, iconParams);
        }
        action.addView(icons, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return action;
    }

    private GradientDrawable createFrostedSurface(Context context) {
        GradientDrawable surface = new GradientDrawable();
        surface.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        surface.setColors(new int[]{0xC42E2F38, 0xAE202127});
        surface.setCornerRadius(dpToPx(context, 18));
        surface.setStroke(dpToPx(context, 1), 0x32FFFFFF);
        return surface;
    }

    private View sizeAction(Context context, int heightDp, int widgetId) {
        int accent = MonetColors.getAccentColor(context);
        LinearLayout action = new LinearLayout(context);
        action.setOrientation(LinearLayout.HORIZONTAL);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription("Change widget height");
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(withAlpha(accent, 0x24));
        fill.setCornerRadius(dpToPx(context, 12));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dpToPx(context, 12));
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), fill, mask));
        action.setPadding(dpToPx(context, 8), 0, dpToPx(context, 8), 0);

        ImageView resizeIcon = new ImageView(context);
        resizeIcon.setImageResource(R.drawable.ic_widget_action_resize);
        resizeIcon.setImageTintList(ColorStateList.valueOf(accent));
        resizeIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        action.addView(resizeIcon, new LinearLayout.LayoutParams(dpToPx(context, 16),
                dpToPx(context, 16)));

        TextView label = new TextView(context);
        label.setText(heightDp <= 0 ? "Auto  ⌄" : heightDp + " dp  ⌄");
        label.setTextColor(accent);
        label.setTextSize(12);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        label.setSingleLine(true);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dpToPx(context, 5);
        action.addView(label, labelParams);
        action.setOnClickListener(v -> {
            WidgetStorage.cycleWidgetHeight(getContext(), widgetId);
            reloadWidgets(true);
        });
        return action;
    }

    private View removeAction(Context context, int widgetId) {
        View action = editIconAction(context,
                new int[]{R.drawable.ic_widget_action_remove}, "Remove widget",
                Color.rgb(255, 105, 97), 0x28FF453A);
        action.setOnClickListener(v -> animateWidgetRemoval(widgetId));
        return action;
    }

    private void animateWidgetRemoval(int widgetId) {
        if (!mWidgetsBeingRemoved.add(widgetId)) return;
        WidgetCardView target = null;
        for (WidgetCardView card : gridCards()) {
            if (card.getWidgetId() == widgetId) {
                target = card;
                break;
            }
        }
        if (target == null) {
            removeWidget(widgetId);
            return;
        }

        target.animate().cancel();
        target.animate()
                .alpha(0f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .translationY(-dpToPx(getContext(), 10))
                .setDuration(190L)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .withEndAction(() -> removeWidget(widgetId))
                .start();
    }

    private void moveWidget(int from, int to) {
        List<Integer> ids = WidgetStorage.getWidgetIds(getContext());
        if (from < 0 || from >= ids.size() || to < 0 || to >= ids.size() || from == to) {
            return;
        }
        java.util.Map<Integer, float[]> origins = new java.util.HashMap<>();
        for (WidgetCardView card : gridCards()) {
            cancelGridMoveAnimation(card);
            origins.put(card.getWidgetId(), new float[]{
                    card.getLeft() + card.getTranslationX(),
                    card.getTop() + card.getTranslationY()});
        }
        // Reordering used to force a full reload. That detached every
        // AppWidgetHostView and rebuilt it asynchronously, producing a visible
        // blank/flicker on every arrow tap. The storage operation already
        // swaps the two placements, so update the attached cards in place.
        WidgetStorage.moveWidget(getContext(), from, to);
        for (WidgetCardView card : gridCards()) {
            int[] placement = WidgetStorage.getPlacement(getContext(), card.getWidgetId());
            if (placement != null) {
                card.setGridPos(placement[0], placement[1], placement[2]);
                layoutCardFromPlacement(card);
            }
        }
        sortCardsByGrid();
        refreshReorderActions();
        animatePendingReorder(origins);
    }

    private void refreshReorderActions() {
        List<WidgetCardView> cards = gridCards();
        int total = cards.size();
        int accent = MonetColors.getAccentColor(getContext());
        for (int pos = 0; pos < total; pos++) {
            WidgetCardView card = cards.get(pos);
            for (int i = 0; i < card.getChildCount(); i++) {
                View child = card.getChildAt(i);
                if (!(child instanceof LinearLayout)) continue;
                LinearLayout bar = (LinearLayout) child;
                for (int j = 0; j < bar.getChildCount(); j++) {
                    View action = bar.getChildAt(j);
                    CharSequence description = action.getContentDescription();
                    if (description == null) continue;
                    boolean up = "Move widget up".contentEquals(description);
                    boolean down = "Move widget down".contentEquals(description);
                    if (!up && !down) continue;
                    boolean enabled = up ? pos > 0 : pos < total - 1;
                    action.setEnabled(enabled);
                    int tint = enabled ? accent : withAlpha(accent, 0x80);
                    if (action instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) action;
                        if (group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout) {
                            LinearLayout icons = (LinearLayout) group.getChildAt(0);
                            if (icons.getChildCount() > 0 && icons.getChildAt(0) instanceof ImageView) {
                                ((ImageView) icons.getChildAt(0)).setImageTintList(
                                        ColorStateList.valueOf(tint));
                            }
                        }
                    }
                    final int delta = up ? -1 : 1;
                    action.setOnClickListener(v -> {
                        int current = WidgetStorage.getWidgetIds(getContext())
                                .indexOf(card.getWidgetId());
                        moveWidget(current, current + delta);
                    });
                }
            }
        }
    }

    private void animatePendingReorder() {
        final java.util.Map<Integer, float[]> origins = mPendingReorderOrigins;
        mPendingReorderOrigins = null;
        animatePendingReorder(origins);
    }

    private void animatePendingReorder(final java.util.Map<Integer, float[]> origins) {
        if (origins == null || mWidgetsContainer == null) return;

        if (mReorderPreDrawListener != null
                && mWidgetsContainer.getViewTreeObserver().isAlive()) {
            mWidgetsContainer.getViewTreeObserver()
                    .removeOnPreDrawListener(mReorderPreDrawListener);
        }
        mReorderPreDrawListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (mReorderPreDrawListener != this) return true;
                if (mWidgetsContainer.getViewTreeObserver().isAlive()) {
                    mWidgetsContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                mReorderPreDrawListener = null;
                for (WidgetCardView card : gridCards()) {
                    float[] old = origins.get(card.getWidgetId());
                    if (old == null) continue;
                    float dx = old[0] - card.getLeft();
                    float dy = old[1] - card.getTop();
                    animateGridTranslationToHome(card, dx, dy);
                }
                return true;
            }
        };
        mWidgetsContainer.getViewTreeObserver().addOnPreDrawListener(mReorderPreDrawListener);
        mWidgetsContainer.requestLayout();
    }

    private void restorePendingScrollPosition() {
        final int scrollY = mPendingScrollRestoreY;
        mPendingScrollRestoreY = -1;
        ScrollView scrollView = mShelfScrollView;
        if (scrollY < 0 || scrollView == null) return;

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (scrollView.getViewTreeObserver().isAlive()) {
                            scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        scrollView.scrollTo(0, scrollY);
                    }
                });
        scrollView.requestLayout();
    }

    private void showMoveWidgetMenu(View anchor, int pos, int total) {
        showWidgetActionSheet(anchor, new int[]{R.drawable.ic_widget_action_up,
                        R.drawable.ic_widget_action_down},
                new String[]{"Move up", "Move down"},
                new String[]{"Move widget up", "Move widget down"},
                new boolean[]{pos > 0, pos < total - 1},
                new Runnable[]{() -> moveWidget(pos, pos - 1), () -> moveWidget(pos, pos + 1)});
    }

    private void showCompactWidgetActions(View anchor, int pos, int total, int widgetId) {
        showWidgetActionSheet(anchor, new int[]{R.drawable.ic_widget_action_up,
                        R.drawable.ic_widget_action_down, R.drawable.ic_widget_action_resize,
                        R.drawable.ic_widget_action_remove},
                new String[]{"Up", "Down", "Height", "Remove"},
                new String[]{"Move widget up", "Move widget down", "Change widget height",
                        "Remove widget"},
                new boolean[]{pos > 0, pos < total - 1, true, true},
                new Runnable[]{() -> moveWidget(pos, pos - 1), () -> moveWidget(pos, pos + 1),
                        () -> {
                            WidgetStorage.cycleWidgetHeight(getContext(), widgetId);
                            reloadWidgets(true);
                        }, () -> animateWidgetRemoval(widgetId)});
    }

    /** A compact, anchored row of icon actions for widgets with very little toolbar width. */
    private void showWidgetActionSheet(View anchor, int[] icons, String[] labels,
                                       String[] descriptions, boolean[] enabled,
                                       Runnable[] actions) {
        Context context = getContext();
        boolean pairedActions = labels.length == 2;
        int surfaceColor = launcherPopupSurfaceColor(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(pairedActions ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER);
        int outerPadding = dpToPx(context, pairedActions ? 8 : 6);
        content.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(surfaceColor);
        surface.setCornerRadius(dpToPx(context, 18));
        surface.setStroke(dpToPx(context, 1), 0x24FFFFFF);
        content.setBackground(surface);
        content.setClipToOutline(true);

        int actionWidth = dpToPx(context, 62);
        int sheetWidth = pairedActions
                ? dpToPx(context, 224)
                : actionWidth * labels.length + outerPadding * 2;
        PopupWindow popup = new PopupWindow(content, sheetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(surfaceColor);
        popupBackground.setCornerRadius(dpToPx(context, 18));
        popup.setBackgroundDrawable(popupBackground);
        popup.setOutsideTouchable(true);
        popup.setElevation(dpToPx(context, 10));

        if (pairedActions) {
            TextView heading = new TextView(context);
            heading.setText("Reorder widget");
            heading.setTextSize(14);
            heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            heading.setTextColor(MonetColors.getNeutralTextColor(context));
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(dpToPx(context, 10), 0, 0, 0);
            content.addView(heading, new LinearLayout.LayoutParams(-1, dpToPx(context, 34)));
        }

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean destructive = icons[i] == R.drawable.ic_widget_action_remove;
            int actionColor = destructive
                    ? Color.rgb(255, 105, 97) : MonetColors.getNeutralTextColor(context);

            LinearLayout item = new LinearLayout(context);
            item.setOrientation(pairedActions ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            item.setGravity(pairedActions ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            int itemHorizontalPadding = dpToPx(context, pairedActions ? 10 : 2);
            item.setPadding(itemHorizontalPadding, dpToPx(context, pairedActions ? 4 : 8),
                    itemHorizontalPadding, dpToPx(context, pairedActions ? 4 : 6));
            item.setEnabled(enabled[i]);
            item.setClickable(enabled[i]);
            item.setFocusable(enabled[i]);
            item.setContentDescription(descriptions[i]);
            GradientDrawable itemFill = new GradientDrawable();
            itemFill.setColor(Color.TRANSPARENT);
            itemFill.setCornerRadius(dpToPx(context, 12));
            GradientDrawable itemMask = new GradientDrawable();
            itemMask.setColor(Color.WHITE);
            itemMask.setCornerRadius(dpToPx(context, 12));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2EFFFFFF),
                    itemFill, itemMask));
            item.setAlpha(enabled[i] ? 1f : 0.38f);

            if (pairedActions) {
                int accent = MonetColors.getAccentColor(context);
                FrameLayout iconSurface = new FrameLayout(context);
                GradientDrawable iconBackground = new GradientDrawable();
                iconBackground.setColor(withAlpha(accent, 0x24));
                iconBackground.setCornerRadius(dpToPx(context, 10));
                iconSurface.setBackground(iconBackground);
                ImageView icon = new ImageView(context);
                icon.setImageResource(icons[i]);
                icon.setImageTintList(ColorStateList.valueOf(accent));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                iconSurface.addView(icon, new FrameLayout.LayoutParams(dpToPx(context, 18),
                        dpToPx(context, 18), Gravity.CENTER));
                LinearLayout.LayoutParams iconSurfaceParams = new LinearLayout.LayoutParams(
                        dpToPx(context, 32), dpToPx(context, 32));
                iconSurfaceParams.rightMargin = dpToPx(context, 12);
                item.addView(iconSurface, iconSurfaceParams);
            } else {
                ImageView icon = new ImageView(context);
                icon.setImageResource(icons[i]);
                icon.setImageTintList(ColorStateList.valueOf(actionColor));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(icon, new LinearLayout.LayoutParams(dpToPx(context, 22),
                        dpToPx(context, 22)));
            }

            TextView title = new TextView(context);
            title.setText(labels[i]);
            title.setTextSize(pairedActions ? 14 : 10);
            title.setTextColor(actionColor);
            title.setGravity(pairedActions ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            title.setSingleLine(true);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams titleParams = pairedActions
                    ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!pairedActions) titleParams.topMargin = dpToPx(context, 5);
            item.addView(title, titleParams);

            item.setOnClickListener(v -> {
                popup.dismiss();
                actions[index].run();
            });
            LinearLayout.LayoutParams itemParams = pairedActions
                    ? new LinearLayout.LayoutParams(-1, dpToPx(context, 48))
                    : new LinearLayout.LayoutParams(actionWidth, dpToPx(context, 68));
            if (pairedActions && i == 1) {
                View divider = new View(context);
                divider.setBackgroundColor(0x24FFFFFF);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1,
                        dpToPx(context, 1));
                dividerParams.setMargins(dpToPx(context, 10), 0, dpToPx(context, 10), 0);
                content.addView(divider, dividerParams);
            }
            content.addView(item, itemParams);
        }

        popup.showAsDropDown(anchor, 0, dpToPx(context, 4), Gravity.END);
    }

    private int launcherPopupSurfaceColor(Context context) {
        int colorId = context.getResources().getIdentifier(
                "system_neutral2_800", "color", "android");
        if (colorId != 0) {
            try {
                return context.getColor(colorId);
            } catch (RuntimeException ignored) { }
        }
        return 0xFF292D35;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private final WidgetCardView.Delegate mCardDelegate = new WidgetCardView.Delegate() {
        @Override
        public void onOptionsRequested(WidgetCardView card) {
            selectCard(card);
        }

        @Override
        public void onDragStarted(WidgetCardView card) {
            mCardGestureActive = true;
            cancelGridMoveAnimation(card);
            if (mSelectedCard == card) {
                deselectCard();
            }
        }

        @Override
        public void onDragEnded(WidgetCardView card, int targetRow, int targetCol) {
            mCardGestureActive = false;
            if (targetRow >= 0 && targetCol >= 0) {
                tryPlace(card, targetRow, targetCol, card.getGridSpanX(), true);
                pushWidgetSize(card);
            } else {
                // Drag start brings the card above every child. If the release
                // has no valid grid target, restore card order so it cannot
                // remain drawn over the static Edit button.
                sortCardsByGrid();
                animateGridCardToHome(card);
            }
            flushPendingReload();
        }
    };

    private final WidgetResizeFrame.Listener mFrameListener = new WidgetResizeFrame.Listener() {
        @Override
        public void onBorderDown(int edges) {
            if (mSelectedCard == null) {
                return;
            }
            mCardGestureActive = true;
            mResizeStartW = mSelectedCard.currentCardWidthPx();
            mResizeStartTranslationX = mSelectedCard.getTranslationX();
            mResizeStartTranslationY = mSelectedCard.getTranslationY();
            View host = mSelectedCard.getHostView();
            View resizeFrame = widgetResizeFrame(host);
            mResizeStartH = resizeFrame != null ? resizeFrame.getHeight() : 0;
            if (resizeFrame != null) {
                int[] frameLoc = new int[2];
                int[] rootLoc = new int[2];
                resizeFrame.getLocationOnScreen(frameLoc);
                getLocationOnScreen(rootLoc);
                mResizeStartLeft = frameLoc[0] - rootLoc[0];
                mResizeStartTop = frameLoc[1] - rootLoc[1];
            }
            if (mResizeStartH <= 0 && resizeFrame != null
                    && resizeFrame.getLayoutParams() != null
                    && resizeFrame.getLayoutParams().height > 0) {
                mResizeStartH = resizeFrame.getLayoutParams().height;
            }
            mResizeStartCol = mSelectedCard.getGridCellX();
            mResizeStartSpan = mSelectedCard.getGridSpanX();
            setResizeBounds(mSelectedCard);
        }

        @Override
        public void onBorderMove(int edges, float dx, float dy) {
            applyFrameResize(edges, dx, dy);
        }

        @Override
        public void onBorderUp(int edges, boolean commit) {
            mCardGestureActive = false;
            if (mSelectedCard != null) {
                if (commit) {
                    clearResizeSnapGuideForReflow();
                    commitFrameResize(edges);
                } else {
                    clearResizeSnapGuide();
                    revertLiveResize(mSelectedCard);
                }
                repositionResizeFrameAfterLayout();
            } else {
                clearResizeSnapGuide();
            }
            flushPendingReload();
        }

        @Override
        public void onButtonTap(int button) {
            if (mSelectedCard == null) {
                return;
            }
            if (button == WidgetResizeFrame.BUTTON_RECONFIGURE) {
                launchConfigure(mSelectedCard.getWidgetId());
            } else if (button == WidgetResizeFrame.BUTTON_REMOVE) {
                animateWidgetRemoval(mSelectedCard.getWidgetId());
            }
        }

        @Override
        public void onPresetSelected(int preset) {
            applySizePreset(preset);
        }

        @Override
        public void onTapOutside() {
            deselectCard();
        }
    };

    private void selectCard(WidgetCardView card) {
        mSelectedCard = card;
        AppWidgetProviderInfo info = null;
        try {
            info = mAppWidgetManager.getAppWidgetInfo(card.getWidgetId());
        } catch (Exception ignored) {
        }
        mResizeFrame.setReconfigureVisible(info != null && info.configure != null);
        int resizeEdges = 0;
        if (info != null) {
            if ((info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0) {
                resizeEdges |= WidgetResizeFrame.EDGE_LEFT | WidgetResizeFrame.EDGE_RIGHT;
            }
            if ((info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
                resizeEdges |= WidgetResizeFrame.EDGE_TOP | WidgetResizeFrame.EDGE_BOTTOM;
            }
        }
        mResizeFrame.setResizableEdges(resizeEdges);
        mResizeFrame.setVisibility(View.VISIBLE);
        repositionResizeFrame();
        mResizeFrame.revealChrome();
    }

    private void deselectCard() {
        clearResizeSnapGuide();
        if (mResizeFrame != null) {
            mResizeFrame.setVisibility(View.GONE);
            mResizeFrame.clearTarget();
        }
        mSelectedCard = null;
    }

    private void repositionResizeFrame() {
        if (mSelectedCard == null || mResizeFrame == null
                || mResizeFrame.getVisibility() != View.VISIBLE) {
            return;
        }
        if (mSelectedCard.getParent() == null || !mSelectedCard.isAttachedToWindow()) {
            deselectCard();
            return;
        }
        View host = mSelectedCard.getHostView();
        View resizeFrame = widgetResizeFrame(host);
        if (resizeFrame == null) {
            return;
        }
        int[] frameLoc = new int[2];
        int[] rootLoc = new int[2];
        resizeFrame.getLocationOnScreen(frameLoc);
        getLocationOnScreen(rootLoc);
        float left = frameLoc[0] - rootLoc[0];
        float top = frameLoc[1] - rootLoc[1];
        float width = currentCardWidthPx(mSelectedCard) * mSelectedCard.getScaleX();
        int height = resizeFrame.getHeight();
        ViewGroup.LayoutParams frameParams = resizeFrame.getLayoutParams();
        if (frameParams != null && frameParams.height >= 0) {
            height = frameParams.height;
        }
        float scaledHeight = Math.max(1, height) * mSelectedCard.getScaleY();
        mResizeFrame.setTargetRect(left, top, left + width, top + scaledHeight);
    }

    private void repositionResizeFrameAfterLayout() {
        if (mWidgetsContainer == null || !mWidgetsContainer.isAttachedToWindow()) {
            repositionResizeFrame();
            return;
        }
        final android.view.ViewTreeObserver observer = mWidgetsContainer.getViewTreeObserver();
        if (!observer.isAlive()) {
            repositionResizeFrame();
            return;
        }
        observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                repositionResizeFrame();
                return true;
            }
        });
    }

    private int currentCardWidthPx(WidgetCardView card) {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp != null && lp.width > 0) {
            return lp.width;
        }
        return Math.max(1, card.getWidth());
    }

    private void setResizeBounds(WidgetCardView card) {
        AppWidgetProviderInfo info = null;
        try {
            info = mAppWidgetManager.getAppWidgetInfo(card.getWidgetId());
        } catch (Exception ignored) {
        }
        int minWidthPx = info == null ? 0 : info.minWidth;
        int minHeightPx = info == null ? 0 : info.minHeight;
        if (info != null) {
            if (info.minResizeWidth > 0
                    && (info.minWidth <= 0 || info.minResizeWidth <= info.minWidth)) {
                minWidthPx = info.minResizeWidth;
            }
            if (info.minResizeHeight > 0
                    && (info.minHeight <= 0 || info.minResizeHeight <= info.minHeight)) {
                minHeightPx = info.minResizeHeight;
            }
        }
        // AppWidgetProviderInfo exposes these dimensions in physical pixels,
        // even though the provider declares them as dp-sized XML dimensions.
        // Do not multiply by display density a second time.
        mResizeMinW = Math.max(1, minWidthPx);
        mResizeMinH = Math.max(1, minHeightPx);
        mResizeMaxW = 0;
        mResizeMaxH = 0;
        if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.maxResizeWidth > 0
                    && (info.minWidth <= 0 || info.maxResizeWidth >= info.minWidth)) {
                mResizeMaxW = info.maxResizeWidth;
            }
            if (info.maxResizeHeight > 0
                    && (info.minHeight <= 0 || info.maxResizeHeight >= info.minHeight)) {
                mResizeMaxH = info.maxResizeHeight;
            }
        }
    }

    private static int clampResize(int value, int minimum, int maximum) {
        int upper = maximum > 0 ? maximum : Integer.MAX_VALUE;
        int lower = Math.min(minimum, upper);
        return Math.max(lower, Math.min(value, upper));
    }

    private void applyFrameResize(int edges, float dx, float dy) {
        WidgetCardView card = mSelectedCard;
        if (card == null || card.getHostView() == null) {
            return;
        }
        boolean horizontal =
                (edges & (WidgetResizeFrame.EDGE_LEFT | WidgetResizeFrame.EDGE_RIGHT)) != 0;
        if (horizontal) {
            int delta = Math.round((edges & WidgetResizeFrame.EDGE_RIGHT) != 0 ? dx : -dx);
            int next = Math.max(1, mResizeStartW + delta);
            int maxW = 0;
            if (mWidgetsContainer instanceof WidgetGridLayout) {
                WidgetGridLayout grid = (WidgetGridLayout) mWidgetsContainer;
                int availableColumns = (edges & WidgetResizeFrame.EDGE_RIGHT) != 0
                        ? WidgetGridLayout.GRID_COLUMNS - mResizeStartCol
                        : mResizeStartCol + mResizeStartSpan;
                maxW = grid.spanWidthPx(availableColumns);
            } else {
                maxW = contentWidthPx();
            }
            if (mResizeMaxW > 0 && (maxW <= 0 || mResizeMaxW < maxW)) {
                maxW = mResizeMaxW;
            }
            next = clampResize(next, mResizeMinW, maxW);
            card.setCardWidthPx(next);
            if ((edges & WidgetResizeFrame.EDGE_LEFT) != 0) {
                card.setTranslationX(mResizeStartTranslationX + mResizeStartW - next);
            }
        }
        if ((edges & (WidgetResizeFrame.EDGE_TOP | WidgetResizeFrame.EDGE_BOTTOM)) != 0) {
            int delta = Math.round((edges & WidgetResizeFrame.EDGE_BOTTOM) != 0 ? dy : -dy);
            int next = clampResize(mResizeStartH + delta, mResizeMinH, mResizeMaxH);
            View host = card.getHostView();
            setWidgetFrameHeight(host, next, true);
            if ((edges & WidgetResizeFrame.EDGE_TOP) != 0) {
                card.setTranslationY(mResizeStartTranslationY + mResizeStartH - next);
            }
        }
        // LayoutParams changes are applied on the next traversal. Keep the
        // resize chrome tied to the finger using the same gesture-start bounds
        // instead of reading the still-stale host position each motion event.
        float left = mResizeStartLeft;
        float top = mResizeStartTop;
        int resizedWidth = currentCardWidthPx(card);
        View resizeFrame = widgetResizeFrame(card.getHostView());
        int resizedHeight = resizeFrame != null && resizeFrame.getLayoutParams() != null
                && resizeFrame.getLayoutParams().height >= 0
                ? resizeFrame.getLayoutParams().height : mResizeStartH;
        float right = left + resizedWidth;
        float bottom = top + resizedHeight;
        if ((edges & WidgetResizeFrame.EDGE_LEFT) != 0) {
            right = mResizeStartLeft + mResizeStartW;
            left = right - resizedWidth;
        } else if ((edges & WidgetResizeFrame.EDGE_RIGHT) != 0) {
            right = mResizeStartLeft + resizedWidth;
        }
        if ((edges & WidgetResizeFrame.EDGE_TOP) != 0) {
            bottom = mResizeStartTop + mResizeStartH;
            top = bottom - resizedHeight;
        } else if ((edges & WidgetResizeFrame.EDGE_BOTTOM) != 0) {
            bottom = mResizeStartTop + resizedHeight;
        }
        mResizeFrame.setTargetRect(left, top, right, bottom);
        updateResizeSnapGuide(edges, horizontal, card);
    }

    /** Show the snapped footprint while a resize is in progress. */
    private void updateResizeSnapGuide(int edges, boolean horizontal, WidgetCardView card) {
        if (!(mWidgetsContainer instanceof WidgetGridLayout)) {
            return;
        }
        WidgetGridLayout grid = (WidgetGridLayout) mWidgetsContainer;
        int span = card.getGridSpanX();
        int col = card.getGridCellX();
        int cell = grid.getCellWidthPx();
        View host = card.getHostView();
        View resizeFrame = widgetResizeFrame(host);
        int height = resizeFrame != null ? resizeFrame.getHeight() : card.getHeight();
        if (resizeFrame != null && resizeFrame.getLayoutParams() != null
                && resizeFrame.getLayoutParams().height >= 0) {
            height = resizeFrame.getLayoutParams().height;
        }
        if (horizontal && cell > 0) {
            span = grid.spanForWidthPx(currentCardWidthPx(card));
            if ((edges & WidgetResizeFrame.EDGE_RIGHT) != 0) {
                col = card.getGridCellX();
            } else {
                col = (mResizeStartCol + mResizeStartSpan) - span;
            }
            col = Math.max(0, Math.min(col, WidgetGridLayout.GRID_COLUMNS - span));
        }
        grid.setDropTarget(card.getGridRow(), col, span, height, card);
    }

    private void clearResizeSnapGuide() {
        if (mWidgetsContainer instanceof WidgetGridLayout) {
            ((WidgetGridLayout) mWidgetsContainer).clearDropTarget();
        }
    }

    private void clearResizeSnapGuideForReflow() {
        if (mWidgetsContainer instanceof WidgetGridLayout) {
            ((WidgetGridLayout) mWidgetsContainer).clearDropTargetForReflow();
        }
    }

    private int contentWidthPx() {
        if (mWidgetsContainer instanceof WidgetGridLayout) {
            WidgetGridLayout grid = (WidgetGridLayout) mWidgetsContainer;
            if (grid.getWidth() > 0) {
                return Math.max(1, grid.getWidth() - grid.getPaddingLeft()
                        - grid.getPaddingRight());
            }
        }
        return -1;
    }

    private void commitFrameResize(int edges) {
        WidgetCardView card = mSelectedCard;
        if (card == null || !(mWidgetsContainer instanceof WidgetGridLayout)) {
            return;
        }
        WidgetGridLayout grid = (WidgetGridLayout) mWidgetsContainer;
        int col = card.getGridCellX();
        int span = card.getGridSpanX();
        if ((edges & (WidgetResizeFrame.EDGE_LEFT | WidgetResizeFrame.EDGE_RIGHT)) != 0) {
            int cell = grid.getCellWidthPx();
            if (cell > 0) {
                int widthPx = currentCardWidthPx(card);
                span = grid.spanForWidthPx(widthPx);
                if ((edges & WidgetResizeFrame.EDGE_RIGHT) != 0) {
                    span = Math.max(1, Math.min(span,
                            WidgetGridLayout.GRID_COLUMNS - col));
                } else {
                    span = Math.max(1, Math.min(span, WidgetGridLayout.GRID_COLUMNS));
                    col = (mResizeStartCol + mResizeStartSpan) - span;
                    col = Math.max(0, Math.min(col,
                            WidgetGridLayout.GRID_COLUMNS - span));
                    span = (mResizeStartCol + mResizeStartSpan) - col;
                    span = Math.max(1, Math.min(span,
                            WidgetGridLayout.GRID_COLUMNS - col));
                }
            }
        }
        if ((edges & (WidgetResizeFrame.EDGE_TOP | WidgetResizeFrame.EDGE_BOTTOM)) != 0) {
            View host = card.getHostView();
            View resizeFrame = widgetResizeFrame(host);
            float density = getContext().getResources().getDisplayMetrics().density;
            int heightPx = resizeFrame != null ? resizeFrame.getHeight() : 0;
            if (resizeFrame != null && resizeFrame.getLayoutParams() != null
                    && resizeFrame.getLayoutParams().height >= 0) {
                // The last touch event can arrive before requestLayout() has
                // completed, so the measured height may still be one frame old.
                heightPx = resizeFrame.getLayoutParams().height;
            }
            int minHeightDp = (int) Math.ceil(mResizeMinH / density);
            int nextHDp = Math.max(minHeightDp, Math.round(heightPx / density));
            WidgetStorage.setWidgetHeightDp(getContext(), card.getWidgetId(), nextHDp);
            synchronized (mPolicyLock) {
                mCardCacheHeight.put(card.getWidgetId(), nextHDp);
            }
        }
        tryPlace(card, card.getGridRow(), col, span);
        pushWidgetSize(card);
    }

    /**
     * Apply an iOS-style size preset to the selected card and reflow the grid.
     * Small = half width / short, Medium = full width / medium,
     * Large = full width / tall.
     */
    private void applySizePreset(int preset) {
        WidgetCardView card = mSelectedCard;
        if (card == null || !(mWidgetsContainer instanceof WidgetGridLayout)) {
            return;
        }
        int span;
        int heightDp;
        switch (preset) {
            case WidgetResizeFrame.PRESET_SMALL:
                span = 2;
                heightDp = WidgetHeightPolicy.SMALL;
                break;
            case WidgetResizeFrame.PRESET_LARGE:
                span = WidgetGridLayout.GRID_COLUMNS;
                heightDp = WidgetHeightPolicy.XLARGE;
                break;
            case WidgetResizeFrame.PRESET_MEDIUM:
            default:
                span = WidgetGridLayout.GRID_COLUMNS;
                heightDp = WidgetHeightPolicy.MEDIUM;
                break;
        }
        WidgetStorage.setWidgetHeightDp(getContext(), card.getWidgetId(), heightDp);
        synchronized (mPolicyLock) {
            mCardCacheHeight.put(card.getWidgetId(), heightDp);
        }
        View host = card.getHostView();
        setWidgetFrameHeight(host, dpToPx(getContext(), heightDp), true);
        int col = Math.max(0, Math.min(card.getGridCellX(),
                WidgetGridLayout.GRID_COLUMNS - span));
        card.setGridPos(card.getGridRow(), col, span);
        tryPlace(card, card.getGridRow(), col, span);
        pushWidgetSize(card);
        repositionResizeFrame();
    }

    private void revertLiveResize(WidgetCardView card) {
        card.setTranslationX(mResizeStartTranslationX);
        card.setTranslationY(mResizeStartTranslationY);
        layoutCardFromPlacement(card);
        View host = card.getHostView();
        if (host != null) {
            int storedDp = WidgetStorage.getWidgetHeightDp(getContext(), card.getWidgetId());
            setWidgetFrameHeight(host, storedDp > 0
                    ? dpToPx(getContext(), storedDp)
                    : ViewGroup.LayoutParams.WRAP_CONTENT, storedDp > 0);
        }
    }

    private void pushWidgetSize(WidgetCardView card) {
        View host = card.getHostView();
        if (host == null || !(host instanceof AppWidgetHostView)) {
            return;
        }
        Context context = getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int spanNow = card.getGridSpanX();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidthDp = Math.max(280, (int) (dm.widthPixels / dm.density) - 32);
        int widthDpNow = Math.round(spanWidthPx(context, spanNow, screenWidthDp) / density);
        int heightDpNow = WidgetStorage.getWidgetHeightDp(context, card.getWidgetId());
        AppWidgetProviderInfo liveInfo = null;
        try {
            liveInfo = mAppWidgetManager.getAppWidgetInfo(card.getWidgetId());
        } catch (Exception ignored) {
        }
        try {
            WidgetSizing.updateHostViewSize(context, (AppWidgetHostView) host, liveInfo,
                    widthDpNow, heightDpNow);
        } catch (Exception ignored) {
        }
    }

    private View widgetResizeFrame(View host) {
        return host != null && host.getParent() instanceof View
                ? (View) host.getParent() : host;
    }

    private void setWidgetFrameHeight(View host, int heightPx, boolean fillFrame) {
        if (host == null) return;
        View frame = widgetResizeFrame(host);
        ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        if (frameParams != null && frameParams.height != heightPx) {
            frameParams.height = heightPx;
            frame.setLayoutParams(frameParams);
        }
        ViewGroup.LayoutParams hostParams = host.getLayoutParams();
        int hostHeight = fillFrame ? ViewGroup.LayoutParams.MATCH_PARENT
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (hostParams != null && hostParams.height != hostHeight) {
            hostParams.height = hostHeight;
            host.setLayoutParams(hostParams);
        }
    }

    private void launchConfigure(int widgetId) {
        AppWidgetProviderInfo info = null;
        try {
            info = mAppWidgetManager.getAppWidgetInfo(widgetId);
        } catch (Exception ignored) {
        }
        if (info == null || info.configure == null) {
            return;
        }
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(info.configure);
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Configure failed: " + widgetId, e);
        }
    }

    private void removeWidget(int widgetId) {
        mWidgetsBeingRemoved.remove(widgetId);
        deselectCard();
        java.util.Map<Integer, float[]> origins = new java.util.HashMap<>();
        for (WidgetCardView card : gridCards()) {
            if (card.getWidgetId() == widgetId) continue;
            cancelGridMoveAnimation(card);
            origins.put(card.getWidgetId(), new float[]{
                    card.getLeft() + card.getTranslationX(),
                    card.getTop() + card.getTranslationY()});
        }
        int removedRow = -1;
        int[] placement = WidgetStorage.getPlacement(getContext(), widgetId);
        if (placement != null) {
            removedRow = Math.max(0, placement[0]);
        }
        try {
            mAppWidgetHost.deleteAppWidgetId(widgetId);
        } catch (Exception ignored) {
        }
        WidgetStorage.removeWidgetId(getContext(), widgetId);
        if (removedRow >= 0) {
            WidgetStorage.closeRowGap(getContext(), removedRow);
        }
        invalidateCachedCard(widgetId);
        mWidgetCostMs.remove(widgetId);
        mFailedWidgetIds.remove(widgetId);

        for (WidgetCardView card : gridCards()) {
            if (card.getWidgetId() == widgetId) {
                detachFromParent(card);
                break;
            }
        }
        List<Integer> remainingIds = WidgetStorage.getWidgetIds(getContext());
        if (remainingIds.isEmpty()) {
            mIsEditMode = false;
            reloadWidgets(true);
            return;
        }

        for (WidgetCardView card : gridCards()) {
            int[] p = WidgetStorage.getPlacement(getContext(), card.getWidgetId());
            if (p != null) {
                card.setGridPos(p[0], p[1], p[2]);
                layoutCardFromPlacement(card);
            }
        }
        sortCardsByGrid();
        persistGridState();
        refreshReorderActions();
        animatePendingReorder(origins);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        repositionResizeFrame();
    }

    private void ensurePlacements(Context context, List<Integer> widgetIds) {
        // Seed occupancy in list order and resolve any collision, so a stale
        // pref that put two widgets on the same row cannot make them overlap.
        java.util.Map<Integer, boolean[]> occupied = new java.util.HashMap<>();
        java.util.ArrayList<Integer> order = new java.util.ArrayList<>();
        int nextRow = 0;
        for (int id : widgetIds) {
            int[] p = WidgetStorage.getPlacement(context, id);
            int span = p == null
                    ? WidgetGridLayout.GRID_COLUMNS
                    : Math.max(1, Math.min(p[2], WidgetGridLayout.GRID_COLUMNS));
            int row = p == null ? nextRow : Math.max(0, p[0]);
            int col = p == null
                    ? 0
                    : Math.max(0, Math.min(p[1], WidgetGridLayout.GRID_COLUMNS - span));
            int[] resolved = resolvePlacement(occupied, row, col, span);
            row = resolved[0];
            col = resolved[1];
            markGridCells(occupied, row, col, span);
            nextRow = Math.max(nextRow, row + 1);
            WidgetStorage.setPlacement(context, id, row, col, span);
            order.add(id);
        }
        if (!order.isEmpty()) {
            WidgetStorage.setWidgetIds(context, order);
        }
    }

    private static int[] resolvePlacement(java.util.Map<Integer, boolean[]> occupied,
            int row, int col, int span) {
        span = Math.max(1, Math.min(span, WidgetGridLayout.GRID_COLUMNS));
        row = Math.max(0, row);
        col = Math.max(0, Math.min(col, WidgetGridLayout.GRID_COLUMNS - span));
        if (gridFootprintFree(occupied, row, col, span)) {
            return new int[]{row, col};
        }
        for (int r = row; r < row + 128; r++) {
            for (int c = 0; c + span <= WidgetGridLayout.GRID_COLUMNS; c++) {
                if (gridFootprintFree(occupied, r, c, span)) {
                    return new int[]{r, c};
                }
            }
        }
        int maxRow = row;
        for (Integer r : occupied.keySet()) {
            maxRow = Math.max(maxRow, r);
        }
        return new int[]{maxRow + 1, 0};
    }

    private java.util.ArrayList<WidgetCardView> gridCards() {
        java.util.ArrayList<WidgetCardView> out = new java.util.ArrayList<>();
        for (int i = 0; i < mWidgetsContainer.getChildCount(); i++) {
            View child = mWidgetsContainer.getChildAt(i);
            if (child instanceof WidgetCardView) {
                out.add((WidgetCardView) child);
            }
        }
        return out;
    }

    private int spanWidthPx(Context context, int span, int screenWidthDp) {
        if (mWidgetsContainer instanceof WidgetGridLayout) {
            int w = ((WidgetGridLayout) mWidgetsContainer).spanWidthPx(span);
            if (w > 0) {
                return w;
            }
        }
        float density = context.getResources().getDisplayMetrics().density;
        float gapDp = WidgetSpacing.dimenPx(context, "dynamic_grid_cell_border_spacing", 16f)
                / density;
        float cellDp = (screenWidthDp - (WidgetGridLayout.GRID_COLUMNS - 1) * gapDp)
                / WidgetGridLayout.GRID_COLUMNS;
        return Math.max(1, Math.round((span * cellDp + (span - 1) * gapDp) * density));
    }

    private static void markGridCells(java.util.Map<Integer, boolean[]> occupied, int row,
            int col, int span) {
        boolean[] cells = occupied.get(row);
        if (cells == null) {
            cells = new boolean[WidgetGridLayout.GRID_COLUMNS];
            occupied.put(row, cells);
        }
        for (int c = col; c < col + span && c < WidgetGridLayout.GRID_COLUMNS; c++) {
            cells[c] = true;
        }
    }

    private static boolean gridFootprintFree(java.util.Map<Integer, boolean[]> occupied,
            int row, int col, int span) {
        boolean[] cells = occupied.get(row);
        if (cells == null) {
            return true;
        }
        for (int c = col; c < col + span && c < WidgetGridLayout.GRID_COLUMNS; c++) {
            if (cells[c]) {
                return false;
            }
        }
        return true;
    }

    private static boolean gridOverlaps(int rowA, int colA, int spanA, int rowB, int colB,
            int spanB) {
        return rowA == rowB && colA < colB + spanB && colB < colA + spanA;
    }

    /**
     * Nearest free cell for a displaced widget, searched outward from the
     * widget's current position so free placement does not reshuffle the whole
     * shelf (the old behaviour packed everything back toward row 0).
     */
    private static int[] nearestVacantCell(java.util.Map<Integer, boolean[]> occupied, int span,
            int preferredRow, int preferredCol) {
        span = Math.max(1, Math.min(span, WidgetGridLayout.GRID_COLUMNS));
        preferredRow = Math.max(0, preferredRow);
        for (int d = 0; d <= 64; d++) {
            int[] below = bestFreeRunInRow(occupied, preferredRow + d, span, preferredCol);
            if (below != null) {
                return new int[]{preferredRow + d, below[0]};
            }
            if (d > 0) {
                int[] above = bestFreeRunInRow(occupied, preferredRow - d, span, preferredCol);
                if (above != null) {
                    return new int[]{preferredRow - d, above[0]};
                }
            }
        }
        int maxRow = 0;
        for (Integer r : occupied.keySet()) {
            maxRow = Math.max(maxRow, r);
        }
        int col = Math.max(0, Math.min(preferredCol, WidgetGridLayout.GRID_COLUMNS - span));
        return new int[]{maxRow + 1, col};
    }

    /** Free run of {@code span} columns in {@code row} closest to {@code preferredCol}. */
    private static int[] bestFreeRunInRow(java.util.Map<Integer, boolean[]> occupied, int row,
            int span, int preferredCol) {
        if (row < 0) {
            return null;
        }
        preferredCol = Math.max(0, Math.min(preferredCol, WidgetGridLayout.GRID_COLUMNS - span));
        boolean[] cells = occupied.get(row);
        if (cells == null) {
            return new int[]{preferredCol};
        }
        int n = cells.length;
        int bestCol = -1;
        int bestDist = Integer.MAX_VALUE;
        int i = 0;
        while (i < n) {
            if (cells[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && !cells[i]) {
                i++;
            }
            int runLen = i - start;
            if (runLen >= span) {
                int pos = Math.max(start, Math.min(preferredCol, i - span));
                int dist = Math.abs(pos - preferredCol);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestCol = pos;
                }
            }
        }
        return bestCol < 0 ? null : new int[]{bestCol};
    }

    private boolean tryPlace(WidgetCardView card, int row, int col, int spanX) {
        return tryPlace(card, row, col, spanX, false);
    }

    /**
     * Place {@code card} at (row, col) with width {@code spanX}.
     *
     * <p>When {@code allowShrink} is set (drag-and-drop), a card that does not
     * fit at its full width can squeeze into a free slot in the target row
     * instead of shoving the occupants to another row, so widgets can be placed
     * side by side regardless of their current size.</p>
     */
    private boolean tryPlace(WidgetCardView card, int row, int col, int spanX, boolean allowShrink) {
        spanX = Math.max(1, Math.min(spanX, WidgetGridLayout.GRID_COLUMNS));
        row = Math.max(0, row);
        int wantCol = Math.max(0, Math.min(col, WidgetGridLayout.GRID_COLUMNS - 1));
        int colExact = Math.max(0, Math.min(wantCol, WidgetGridLayout.GRID_COLUMNS - spanX));
        java.util.ArrayList<WidgetCardView> others = gridCards();
        others.remove(card);
        java.util.Map<Integer, boolean[]> occupied = new java.util.HashMap<>();
        for (WidgetCardView c : others) {
            markGridCells(occupied, c.getGridRow(), c.getGridCellX(), c.getGridSpanX());
        }
        if (gridFootprintFree(occupied, row, colExact, spanX)) {
            card.setGridPos(row, colExact, spanX);
            afterGridChange();
            return true;
        }
        if (allowShrink) {
            boolean[] cells = occupied.get(row);
            if (cells != null) {
                int[] fit = fitSpanInRow(cells, wantCol, spanX);
                if (fit != null) {
                    card.setGridPos(row, fit[0], fit[1]);
                    afterGridChange();
                    return true;
                }
            }
        }
        java.util.ArrayList<WidgetCardView> occupants = new java.util.ArrayList<>();
        for (WidgetCardView c : others) {
            if (gridOverlaps(c.getGridRow(), c.getGridCellX(), c.getGridSpanX(),
                    row, colExact, spanX)) {
                occupants.add(c);
            }
        }
        markGridCells(occupied, row, colExact, spanX);
        card.setGridPos(row, colExact, spanX);
        occupants.sort((a, b) -> a.getGridRow() != b.getGridRow()
                ? a.getGridRow() - b.getGridRow()
                : a.getGridCellX() - b.getGridCellX());
        int maxRow = row;
        for (Integer r : occupied.keySet()) {
            maxRow = Math.max(maxRow, r);
        }
        for (WidgetCardView o : occupants) {
            int[] free = nearestVacantCell(occupied, o.getGridSpanX(),
                    o.getGridRow(), o.getGridCellX());
            markGridCells(occupied, free[0], free[1], o.getGridSpanX());
            maxRow = Math.max(maxRow, free[0]);
            o.setGridPos(free[0], free[1], o.getGridSpanX());
        }
        afterGridChange();
        return true;
    }

    /**
     * Find a free run of columns in {@code cells} near {@code wantCol} and return
     * the best {@code {col, span}} using one of {@link WidgetGridLayout#SPAN_SIZES},
     * or {@code null} when the row has no room at all.
     */
    private static int[] fitSpanInRow(boolean[] cells, int wantCol, int maxSpan) {
        int n = cells.length;
        int bestSpan = 0;
        int bestCol = -1;
        int bestDist = Integer.MAX_VALUE;
        int i = 0;
        while (i < n) {
            if (cells[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && !cells[i]) {
                i++;
            }
            int end = i - 1;
            int runLen = end - start + 1;
            int span = 1;
            for (int size : WidgetGridLayout.SPAN_SIZES) {
                if (size <= maxSpan && size <= runLen) {
                    span = size;
                    break;
                }
            }
            int pos = Math.max(start, Math.min(wantCol, end - span + 1));
            int dist = Math.abs(pos - wantCol);
            if (span > bestSpan || (span == bestSpan && dist < bestDist)) {
                bestSpan = span;
                bestCol = pos;
                bestDist = dist;
            }
        }
        if (bestSpan <= 0 || bestCol < 0) {
            return null;
        }
        return new int[]{bestCol, bestSpan};
    }

    private void afterGridChange() {
        java.util.Map<WidgetCardView, float[]> before = new java.util.HashMap<>();
        for (WidgetCardView c : gridCards()) {
            float scaleX = c.getScaleX();
            float pivotX = c.getPivotX();
            float visibleLeft = c.getLeft() + c.getTranslationX()
                    + pivotX * (1f - scaleX);
            float visibleTop = c.getTop() + c.getTranslationY();
            float visibleWidth = currentCardWidthPx(c) * scaleX;
            cancelGridMoveAnimation(c);
            before.put(c, new float[]{visibleLeft, visibleTop, visibleWidth});
        }
        // Free placement: row indices are preserved (no compaction), so empty
        // cells and rows stay where the user left them.
        sortCardsByGrid();
        // A drop may have changed a card's span (shrink-to-fit beside another
        // widget), so re-derive every card's width from its committed span.
        for (WidgetCardView c : gridCards()) {
            layoutCardFromPlacement(c);
        }
        persistGridState();
        animateGridReflow(before);
    }

    /**
     * Animate cards from their old slots to new ones after a reorder/resize,
     * mirroring Launcher3's {@code CellLayout.animateChildToPosition} timing.
     */
    private void animateGridReflow(final java.util.Map<WidgetCardView, float[]> before) {
        final ViewGroup container = mWidgetsContainer;
        if (mReflowListener != null) {
            if (container.getViewTreeObserver().isAlive()) {
                container.getViewTreeObserver().removeOnPreDrawListener(mReflowListener);
            }
            mReflowListener = null;
        }
        mReflowListener = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (mReflowListener != this) {
                    return true;
                }
                if (container.getViewTreeObserver().isAlive()) {
                    container.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                mReflowListener = null;
                for (WidgetCardView c : gridCards()) {
                    float[] old = before.get(c);
                    if (old == null || c.isDragging()) {
                        continue;
                    }
                    float dx = old[0] - c.getLeft();
                    float dy = old[1] - c.getTop();
                    float oldWidth = old.length > 2 ? old[2] : c.getWidth();
                    float widthScale = c.getWidth() > 0
                            ? oldWidth / c.getWidth() : 1f;
                    if (dx == 0f && dy == 0f && Math.abs(widthScale - 1f) < 0.001f) {
                        continue;
                    }
                    animateGridTranslationToHome(c, dx, dy, widthScale);
                }
                return true;
            }
        };
        container.getViewTreeObserver().addOnPreDrawListener(mReflowListener);
        container.requestLayout();
    }

    private void animateGridCardToHome(WidgetCardView card) {
        animateGridTranslationToHome(card, card.getTranslationX(), card.getTranslationY());
    }

    private void animateGridTranslationToHome(WidgetCardView card, float fromX, float fromY) {
        animateGridTranslationToHome(card, fromX, fromY, 1f);
    }

    private void animateGridTranslationToHome(WidgetCardView card, float fromX, float fromY,
                                               float fromScaleX) {
        cancelGridMoveAnimation(card);
        if (fromX == 0f && fromY == 0f && Math.abs(fromScaleX - 1f) < 0.001f) return;
        card.setPivotX(0f);
        card.setTranslationX(fromX);
        card.setTranslationY(fromY);
        card.setScaleX(fromScaleX);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        mGridMoveAnimators.put(card, animator);
        float distance = (float) Math.hypot(fromX, fromY);
        WidgetGridLayout grid = card.getParent() instanceof WidgetGridLayout
                ? (WidgetGridLayout) card.getParent() : null;
        float cellWidth = grid != null ? Math.max(1, grid.getCellWidthPx()) : dpToPx(getContext(), 96);
        long duration = Math.round(Math.max(240f, Math.min(460f,
                240f + (distance / cellWidth) * 70f)));
        animator.setDuration(duration);
        animator.setInterpolator(Interpolators.DECELERATE_1_5);
        animator.addUpdateListener(value -> {
            if (mGridMoveAnimators.get(card) != animator) return;
            float progress = (Float) value.getAnimatedValue();
            card.setTranslationX(fromX * (1f - progress));
            card.setTranslationY(fromY * (1f - progress));
            card.setScaleX(fromScaleX + (1f - fromScaleX) * progress);
            if (card == mSelectedCard) {
                repositionResizeFrame();
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mGridMoveAnimators.get(card) == animator) {
                    mGridMoveAnimators.remove(card);
                    card.setTranslationX(0f);
                    card.setTranslationY(0f);
                    card.setScaleX(1f);
                    card.setScaleY(1f);
                    card.setPivotX(card.getWidth() / 2f);
                    if (card == mSelectedCard) {
                        repositionResizeFrame();
                    }
                }
            }
        });
        animator.start();
    }

    private void cancelGridMoveAnimation(WidgetCardView card) {
        android.animation.ValueAnimator animator = mGridMoveAnimators.remove(card);
        if (animator != null) {
            animator.cancel();
            card.setScaleX(1f);
            card.setPivotX(card.getWidth() / 2f);
        }
    }

    private void sortCardsByGrid() {
        java.util.ArrayList<WidgetCardView> cards = gridCards();
        cards.sort((a, b) -> a.getGridRow() != b.getGridRow()
                ? a.getGridRow() - b.getGridRow()
                : a.getGridCellX() - b.getGridCellX());

        // Detach every card child by index. Doing it this way also clears any
        // stray duplicate entry, so the re-add below can never hit
        // "The specified child already has a parent."
        for (int i = mWidgetsContainer.getChildCount() - 1; i >= 0; i--) {
            if (mWidgetsContainer.getChildAt(i) instanceof WidgetCardView) {
                mWidgetsContainer.removeViewAt(i);
            }
        }

        java.util.HashSet<WidgetCardView> seen = new java.util.HashSet<>();
        int index = 0;
        for (WidgetCardView card : cards) {
            if (!seen.add(card)) {
                continue;
            }
            detachFromParent(card);
            if (card.getParent() == null) {
                mWidgetsContainer.addView(card,
                        Math.min(index, mWidgetsContainer.getChildCount()));
                index++;
            }
        }
    }

    private static void detachFromParent(View view) {
        android.view.ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private void addCardChild(View child, int at) {
        // Never assume the view is detached: a reload can race with an
        // in-flight drag, so the card may still be attached.
        detachFromParent(child);
        mWidgetsContainer.addView(child,
                Math.min(Math.max(0, at), mWidgetsContainer.getChildCount()));
    }

    private void prepareWidgetEntrance(View card, int widgetId) {
        if (widgetId != mPendingEntranceWidgetId) return;
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setTranslationY(dpToPx(getContext(), 18));
    }

    private void startWidgetEntrance(View card, int widgetId) {
        if (widgetId != mPendingEntranceWidgetId) return;
        mPendingEntranceWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        card.postOnAnimation(() -> {
            if (!card.isAttachedToWindow()) return;
            card.animate().cancel();
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(360L)
                    .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                    .start();
        });
    }

    private void flushPendingReload() {
        if (!mPendingReload) {
            return;
        }
        boolean force = mPendingReloadForce;
        mPendingReload = false;
        mPendingReloadForce = false;
        reloadWidgets(force);
    }

    private void layoutCardFromPlacement(WidgetCardView card) {
        int span = card.getGridSpanX();
        if (span >= WidgetGridLayout.GRID_COLUMNS) {
            card.setCardWidthPx(ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            // Let the grid derive the width from the committed span; only a live
            // resize is allowed to push an explicit pixel width.
            card.setCardWidthPx(ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void persistGridState() {
        final Context context = getContext();
        java.util.ArrayList<WidgetCardView> cards = gridCards();
        cards.sort((a, b) -> a.getGridRow() != b.getGridRow()
                ? a.getGridRow() - b.getGridRow()
                : a.getGridCellX() - b.getGridCellX());
        List<Integer> order = new ArrayList<>();
        for (WidgetCardView c : cards) {
            order.add(c.getWidgetId());
            WidgetStorage.setPlacement(context, c.getWidgetId(), c.getGridRow(),
                    c.getGridCellX(), c.getGridSpanX());
        }
        if (order.isEmpty()) {
            return;
        }
        WidgetStorage.setWidgetIds(context, order);
        synchronized (mPolicyLock) {
            mLastInflatedIds = new ArrayList<>(order);
        }
    }
}
