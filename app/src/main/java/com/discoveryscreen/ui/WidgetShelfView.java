package com.discoveryscreen.ui;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.discoveryscreen.widget.WidgetHostManager;
import com.discoveryscreen.widget.WidgetStorage;
import java.util.List;

public class WidgetShelfView extends FrameLayout {
    private static final String TAG = "WidgetShelfView";

    public interface SwipeProgressListener {
        void onProgress(float progress);
    }

    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;
    private LinearLayout mWidgetsContainer;
    private View mPlaceholderCard;
    private Button mBtnEdit;
    private boolean mIsEditMode = false;
    private Runnable mOnCloseListener;
    private SwipeProgressListener mSwipeProgressListener;

    private float mDownX;
    private float mDownY;
    private boolean mIsHorizontalSwipe = false;
    private int mTouchSlop;

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

    public void setSwipeProgressListener(SwipeProgressListener listener) {
        this.mSwipeProgressListener = listener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
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
                // Detect leftward swipe to dismiss towards home screen
                if (dx < -mTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
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
                if (dx < 0) {
                    setTranslationX(dx);
                    int w = getWidth() > 0 ? getWidth() : 1080;
                    float progress = Math.max(0.0f, 1.0f + dx / w);
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
                if (totalDx < -width * 0.15f) {
                    if (mOnCloseListener != null) {
                        mOnCloseListener.run();
                    }
                } else {
                    animate().translationX(0).alpha(1.0f).setDuration(160).setUpdateListener(animation -> {
                        if (mSwipeProgressListener != null) {
                            int w = getWidth() > 0 ? getWidth() : 1080;
                            float progress = Math.max(0.0f, 1.0f + getTranslationX() / w);
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
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAppWidgetManager = AppWidgetManager.getInstance(context);
        mAppWidgetHost = WidgetHostManager.getHost(context);

        View root = LayoutInflater.from(context).inflate(
            context.getResources().getIdentifier("widget_shelf_overlay", "layout", context.getPackageName()),
            this, true
        );

        adjustScrollPaddingForCutout(root);

        // Remove scrolling indicator
        int scrollId = context.getResources().getIdentifier("shelf_scroll", "id", context.getPackageName());
        ScrollView scrollView = root.findViewById(scrollId);
        if (scrollView != null) {
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setHorizontalScrollBarEnabled(false);
        }

        int containerId = context.getResources().getIdentifier("shelf_widgets_container", "id", context.getPackageName());
        int placeholderId = context.getResources().getIdentifier("shelf_placeholder", "id", context.getPackageName());
        int btnPlaceholderAddId = context.getResources().getIdentifier("btn_placeholder_add", "id", context.getPackageName());
        int btnEditId = context.getResources().getIdentifier("btn_shelf_edit", "id", context.getPackageName());

        mWidgetsContainer = root.findViewById(containerId);
        mPlaceholderCard = root.findViewById(placeholderId);
        mBtnEdit = root.findViewById(btnEditId);

        Button btnPlaceholderAdd = root.findViewById(btnPlaceholderAddId);

        View.OnClickListener addListener = v -> {
            if (mOnCloseListener != null) {
                mOnCloseListener.run();
            }
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("action", "pick_widget");
            context.startActivity(intent);
        };

        if (btnPlaceholderAdd != null) btnPlaceholderAdd.setOnClickListener(addListener);

        if (mBtnEdit != null) {
            mBtnEdit.setOnClickListener(v -> {
                mIsEditMode = !mIsEditMode;
                mBtnEdit.setText(mIsEditMode ? "Done" : "Edit");
                reloadWidgets();
            });
        }

        applyMonetColors(context);
        reloadWidgets();
    }

    private void adjustScrollPaddingForCutout(View root) {
        int scrollId = getResources().getIdentifier("shelf_scroll", "id", getContext().getPackageName());
        View scrollView = root.findViewById(scrollId);
        if (scrollView != null) {
            int statusBarHeight = getStatusBarHeight();
            int padTop = Math.max(statusBarHeight + dpToPx(getContext(), 12), dpToPx(getContext(), 40));
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
    }

    public void startListening() {
        WidgetHostManager.ensureListening(getContext());
    }

    public void stopListening() {
        // Shared host manages listening
    }

    public void reloadWidgets() {
        Context context = getContext();
        List<Integer> widgetIds = WidgetStorage.getWidgetIds(context);

        applyMonetColors(context);

        // Remove only dynamic widget views, keep placeholder and edit button
        for (int i = mWidgetsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = mWidgetsContainer.getChildAt(i);
            if (child != mPlaceholderCard && child != mBtnEdit) {
                mWidgetsContainer.removeViewAt(i);
            }
        }

        if (widgetIds.isEmpty()) {
            if (mPlaceholderCard != null) mPlaceholderCard.setVisibility(View.VISIBLE);
            if (mBtnEdit != null) mBtnEdit.setVisibility(View.GONE);
            return;
        }

        if (mPlaceholderCard != null) mPlaceholderCard.setVisibility(View.GONE);
        if (mBtnEdit != null) mBtnEdit.setVisibility(View.VISIBLE);

        int insertIndex = 0;
        int cardBgId = context.getResources().getIdentifier("card_background", "drawable", context.getPackageName());
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidthDp = Math.max(280, (int) (dm.widthPixels / dm.density) - 32);

        for (int i = 0; i < widgetIds.size(); i++) {
            final int pos = i;
            final int widgetId = widgetIds.get(i);
            AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(widgetId);
            if (info == null) {
                continue;
            }

            int heightDp = WidgetStorage.getWidgetHeightDp(context, widgetId);

            // Container for widget (frameless, borderless)
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, 0, 0, 0);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, dpToPx(context, 16));
            card.setLayoutParams(cardParams);

            // Edit toolbar shown when Edit mode is active
            if (mIsEditMode) {
                LinearLayout editBar = new LinearLayout(context);
                editBar.setOrientation(LinearLayout.HORIZONTAL);
                editBar.setGravity(Gravity.CENTER_VERTICAL);
                editBar.setBackgroundResource(cardBgId);
                editBar.setPadding(dpToPx(context, 8), dpToPx(context, 2), dpToPx(context, 8), dpToPx(context, 2));

                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 38)
                );
                barParams.setMargins(0, 0, 0, dpToPx(context, 8));
                editBar.setLayoutParams(barParams);

                // Move Up
                Button btnUp = new Button(context);
                btnUp.setText("▲");
                btnUp.setTextSize(13);
                btnUp.setBackground(null);
                btnUp.setTextColor(pos > 0 ? Color.WHITE : Color.parseColor("#44FFFFFF"));
                btnUp.setEnabled(pos > 0);
                btnUp.setOnClickListener(v -> {
                    WidgetStorage.moveWidget(context, pos, pos - 1);
                    reloadWidgets();
                });
                editBar.addView(btnUp);

                // Move Down
                Button btnDown = new Button(context);
                btnDown.setText("▼");
                btnDown.setTextSize(13);
                btnDown.setBackground(null);
                btnDown.setTextColor(pos < widgetIds.size() - 1 ? Color.WHITE : Color.parseColor("#44FFFFFF"));
                btnDown.setEnabled(pos < widgetIds.size() - 1);
                btnDown.setOnClickListener(v -> {
                    WidgetStorage.moveWidget(context, pos, pos + 1);
                    reloadWidgets();
                });
                editBar.addView(btnDown);

                // Size toggle button
                Button btnSize = new Button(context);
                btnSize.setText(heightDp <= 0 ? "Size: Auto" : "Size: " + heightDp + "dp");
                btnSize.setTextSize(12);
                btnSize.setBackground(null);
                btnSize.setTextColor(MonetColors.getAccentColor(context));
                btnSize.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                btnSize.setOnClickListener(v -> {
                    WidgetStorage.cycleWidgetHeight(context, widgetId);
                    reloadWidgets();
                });
                editBar.addView(btnSize);

                // Remove button
                Button btnDelete = new Button(context);
                btnDelete.setText("✕ Remove");
                btnDelete.setTextColor(Color.parseColor("#FF453A"));
                btnDelete.setTextSize(12);
                btnDelete.setBackground(null);
                btnDelete.setOnClickListener(v -> {
                    mAppWidgetHost.deleteAppWidgetId(widgetId);
                    WidgetStorage.removeWidgetId(context, widgetId);
                    reloadWidgets();
                });
                editBar.addView(btnDelete);

                card.addView(editBar);
            }

            // Widget Host View
            try {
                AppWidgetHostView hostView = mAppWidgetHost.createView(context, widgetId, info);

                int effectiveHeightDp = heightDp > 0 ? heightDp : (info.minHeight > 0 ? info.minHeight : 150);
                Bundle options = new Bundle();
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, screenWidthDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, screenWidthDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, effectiveHeightDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, effectiveHeightDp);
                try {
                    hostView.updateAppWidgetSize(options, screenWidthDp, effectiveHeightDp, screenWidthDp, effectiveHeightDp);
                } catch (Exception ignored) {}

                if (heightDp > 0) {
                    int heightPx = dpToPx(context, heightDp);
                    LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        heightPx
                    );
                    card.addView(hostView, hostParams);
                } else {
                    LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    card.addView(hostView, hostParams);
                }

                mWidgetsContainer.addView(card, insertIndex);
                insertIndex++;
            } catch (Exception e) {
                Log.e(TAG, "Failed to inflate widget: " + widgetId, e);
            }
        }

        // When in edit mode, add a "+ Add Widget" action button
        if (mIsEditMode) {
            Button btnAddMore = new Button(context);
            btnAddMore.setText("+ Add Widget");
            btnAddMore.setTextColor(Color.WHITE);
            btnAddMore.setTextSize(14);
            btnAddMore.setBackgroundResource(cardBgId);
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 44)
            );
            addLp.setMargins(0, dpToPx(context, 8), 0, dpToPx(context, 16));
            btnAddMore.setLayoutParams(addLp);
            btnAddMore.setOnClickListener(v -> {
                if (mOnCloseListener != null) {
                    mOnCloseListener.run();
                }
                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("action", "pick_widget");
                context.startActivity(intent);
            });
            mWidgetsContainer.addView(btnAddMore, insertIndex);
        }
    }
}
