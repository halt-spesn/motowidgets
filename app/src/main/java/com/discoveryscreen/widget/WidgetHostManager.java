package com.discoveryscreen.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

public class WidgetHostManager {
    private static final String TAG = "WidgetHostManager";
    public static final int APPWIDGET_HOST_ID = 2048;
    // A host-specific option, merged with (never replacing) provider sizing/options.
    public static final String OPTION_HOST_REFRESH = "com.discoveryscreen.hostRefresh";
    private static final long LIVE_REFRESH_INTERVAL_MS = 5000;
    private static final long FAST_UPDATE_INTERVAL_MS = 2000;
    private static final long LIVE_UPDATE_MEMORY_MS = 30000;
    private static ShelfAppWidgetHost sHost;
    private static boolean sListening = false;

    private static class ShelfAppWidgetHost extends AppWidgetHost {
        private final SparseArray<AppWidgetHostView> mRegisteredViews = new SparseArray<>();
        private final SparseArray<UpdateState> mUpdateStates = new SparseArray<>();
        private final Context mContext;
        private final Handler mRefreshHandler = new Handler(Looper.getMainLooper());
        private final Rect mVisibleRect = new Rect();
        private final Runnable mRefreshVisibleWidgets = this::refreshVisibleWidgets;
        private AppWidgetHostView mReusableView;

        private static class UpdateState {
            long lastUpdate;
            long fastStarted;
            long liveUntil;
            long lastRefresh;
            int fastUpdates;
            boolean visible;
        }

        ShelfAppWidgetHost(Context context, int hostId) {
            super(context, hostId);
            mContext = context;
        }

        @Override
        protected AppWidgetHostView onCreateView(Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
            AppWidgetHostView view = mReusableView != null
                    && mReusableView.getAppWidgetId() == appWidgetId
                    ? mReusableView : new RoundedAppWidgetHostView(context);
            mRegisteredViews.put(appWidgetId, view);
            return view;
        }

        void recordUpdate(AppWidgetHostView view, RemoteViews views) {
            int id = view.getAppWidgetId();
            if (views != null && mRegisteredViews.get(id) == view) {
                UpdateState state = stateFor(id);
                long now = SystemClock.uptimeMillis();
                long interval = now - state.lastUpdate;
                if (state.lastUpdate != 0 && interval <= FAST_UPDATE_INTERVAL_MS) {
                    state.fastUpdates = Math.min(3, state.fastUpdates + 1);
                } else {
                    state.fastUpdates = 0;
                    state.fastStarted = now;
                }
                // Several initial/cached snapshots can arrive together even
                // for a static widget. Require a sustained stream of frames.
                if (state.fastUpdates >= 3 && now - state.fastStarted >= 1000) {
                    state.liveUntil = now + LIVE_UPDATE_MEMORY_MS;
                }
                state.lastUpdate = now;
            }
        }

        private UpdateState stateFor(int id) {
            UpdateState state = mUpdateStates.get(id);
            if (state == null) {
                state = new UpdateState();
                mUpdateStates.put(id, state);
            }
            return state;
        }

        void scheduleVisibleRefresh() {
            mRefreshHandler.removeCallbacks(mRefreshVisibleWidgets);
            mRefreshHandler.post(mRefreshVisibleWidgets);
        }

        private boolean isVisible(AppWidgetHostView view) {
            if (!view.isAttachedToWindow() || !view.isShown()
                    || view.getWindowVisibility() != View.VISIBLE
                    || !view.getGlobalVisibleRect(mVisibleRect)) return false;
            // The launcher keeps the closed shelf attached, translated outside
            // its window and transparent. Attachment alone is not visibility.
            View ancestor = view;
            while (ancestor != null) {
                if (ancestor.getAlpha() <= 0.01f) return false;
                ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
            }
            return true;
        }

        private void refreshVisibleWidgets() {
            if (!sListening) return;
            long now = SystemClock.uptimeMillis();
            PowerManager power = mContext.getSystemService(PowerManager.class);
            boolean interactive = power != null && power.isInteractive();
            boolean attached = false;
            AppWidgetManager manager = AppWidgetManager.getInstance(mContext);
            for (int i = 0; i < mRegisteredViews.size(); i++) {
                AppWidgetHostView view = mRegisteredViews.valueAt(i);
                attached |= view.isAttachedToWindow();
                UpdateState state = stateFor(mRegisteredViews.keyAt(i));
                boolean visible = interactive && isVisible(view);
                if (visible && (!state.visible || state.liveUntil > now)
                        && (state.lastRefresh == 0 || now - state.lastRefresh >= LIVE_REFRESH_INTERVAL_MS)) {
                    // Android can freeze a cached provider even while its host
                    // is visible. Notify it through the public host-options API
                    // on reveal, then only for providers observed sending live
                    // frames. This also wakes a frozen/non-exported receiver.
                    Bundle options = new Bundle();
                    options.putLong(OPTION_HOST_REFRESH, now);
                    state.lastRefresh = now;
                    try {
                        manager.updateAppWidgetOptions(view.getAppWidgetId(), options);
                    } catch (RuntimeException error) {
                        Log.w(TAG, "Unable to refresh visible widget " + view.getAppWidgetId(), error);
                    }
                }
                state.visible = visible;
            }
            // No alarms or wake locks: hidden widgets receive no refreshes.
            if (attached) mRefreshHandler.postDelayed(mRefreshVisibleWidgets, 1000);
        }

        @Override
        protected void clearViews() {
            super.clearViews();
            if (mRegisteredViews != null) mRegisteredViews.clear();
            if (mUpdateStates != null) mUpdateStates.clear();
            if (mRefreshHandler != null) mRefreshHandler.removeCallbacks(mRefreshVisibleWidgets);
        }

        @Override
        public void deleteAppWidgetId(int appWidgetId) {
            super.deleteAppWidgetId(appWidgetId);
            mRegisteredViews.remove(appWidgetId);
            mUpdateStates.remove(appWidgetId);
        }

        @Override
        public void onAppWidgetRemoved(int appWidgetId) {
            super.onAppWidgetRemoved(appWidgetId);
            mRegisteredViews.remove(appWidgetId);
            mUpdateStates.remove(appWidgetId);
        }

        void activateView(Context context, AppWidgetHostView view) {
            int id = view.getAppWidgetId();
            if (mRegisteredViews.get(id) == view || view.getAppWidgetInfo() == null) return;
            // AppWidgetHost routes updates to only its most recently created
            // view for an ID. Re-register the cached foreground view through
            // createView, retaining its parent and fetching the latest snapshot.
            mReusableView = view;
            try {
                createView(context, id, view.getAppWidgetInfo());
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to restore widget update registration for " + id, error);
            } finally {
                mReusableView = null;
            }
        }
    }

    public static synchronized AppWidgetHost getHost(Context context) {
        if (sHost == null) {
            sHost = new ShelfAppWidgetHost(context.getApplicationContext(), APPWIDGET_HOST_ID);
        }
        return sHost;
    }

    /** Restore live updates to cached views when their shelf/preview becomes active. */
    public static synchronized void activateHostViews(Context context, View root) {
        if (root == null) return;
        ensureListening(context);
        activateViews(context, root);
        sHost.scheduleVisibleRefresh();
    }

    static synchronized void onHostViewAttached() {
        if (sHost != null) sHost.scheduleVisibleRefresh();
    }

    static synchronized void onHostWidgetUpdate(AppWidgetHostView view, RemoteViews views) {
        if (sHost != null) sHost.recordUpdate(view, views);
    }

    private static void activateViews(Context context, View view) {
        if (view instanceof AppWidgetHostView) {
            sHost.activateView(context, (AppWidgetHostView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                activateViews(context, group.getChildAt(i));
            }
        }
    }

    public static synchronized void ensureListening(Context context) {
        if (sListening) return;
        try {
            getHost(context).startListening();
            sListening = true;
            sHost.scheduleVisibleRefresh();
            Log.d(TAG, "AppWidgetHost listening started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting AppWidgetHost listening", e);
        }
    }

    public static synchronized void stopListening(Context context) {
        if (sHost == null || !sListening) return;
        try {
            sHost.stopListening();
            Log.d(TAG, "AppWidgetHost listening stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping AppWidgetHost listening", e);
        } finally {
            sListening = false;
            sHost.mRefreshHandler.removeCallbacks(sHost.mRefreshVisibleWidgets);
        }
    }
}
