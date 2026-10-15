package com.discoveryscreen.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import com.discoveryscreen.ui.WidgetShelfView;
import com.discoveryscreen.widget.WidgetHostManager;

public class EvieOverlayService extends Service {
    private static final String TAG = "EvieOverlayService";

    // Message codes defined by Launcher's EvieApi
    public static final int MSG_ATTACH_WINDOW = 0;
    public static final int MSG_START_SCROLL = 1;
    public static final int MSG_UPDATE_SCROLL = 2;
    public static final int MSG_END_SCROLL = 3;
    public static final int MSG_PAUSE = 4;
    public static final int MSG_DETACH_WINDOW = 5;
    public static final int MSG_RESUME = 6;
    public static final int MSG_SERVICE_STATE_CHANGED = 8;
    public static final int MSG_LIGHT_STATUS_BAR = 10;
    public static final int MSG_HIDE = 11;
    public static final int MSG_TURN_OFF = 13;
    public static final int MSG_SETTINGS_INTENT = 14;
    public static final int MSG_LIGHT_NAV_BAR = 15;

    private WindowManager mWindowManager;
    private WidgetShelfView mShelfView;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private Messenger mLocalMessenger;
    private Messenger mLauncherMessenger;
    private boolean mIsAttached = false;
    private float mLastProgress = 0.0f;
    private int mScreenWidth = 1080;
    private ValueAnimator mScrollAnimator;
    private int mLastAppliedBlur = -1;
    private float mLastAppliedDim = -1.0f;

    private final Handler mIncomingHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ATTACH_WINDOW:
                    handleAttachWindow(msg);
                    break;
                case MSG_START_SCROLL:
                    handleStartScroll();
                    break;
                case MSG_UPDATE_SCROLL:
                    handleUpdateScroll(Float.intBitsToFloat(msg.arg1));
                    break;
                case MSG_END_SCROLL:
                    handleEndScroll(Float.intBitsToFloat(msg.arg1));
                    break;
                case MSG_PAUSE:
                    Log.d(TAG, "Launcher paused - hiding overlay so launched app is visible");
                    handleHide();
                    break;
                case MSG_RESUME:
                    Log.d(TAG, "Launcher resumed, lastProgress=" + mLastProgress);
                    WidgetHostManager.ensureListening(EvieOverlayService.this);
                    if (mShelfView != null) {
                        mShelfView.setVisibility(View.VISIBLE);
                        if (mWindowLayoutParams != null) {
                            mWindowLayoutParams.alpha = 1.0f;
                            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                                mWindowLayoutParams.setBlurBehindRadius(0);
                                mWindowLayoutParams.dimAmount = 0.0f;
                                mLastAppliedBlur = 0;
                                mLastAppliedDim = 0.0f;
                            }
                            safeUpdateViewLayout();
                        }
                        mShelfView.reloadWidgets();
                    }
                    break;
                case MSG_DETACH_WINDOW:
                    handleDetachWindow();
                    break;
                case MSG_HIDE:
                case MSG_TURN_OFF:
                    handleHide();
                    break;
                default:
                    Log.d(TAG, "Unhandled message what: " + msg.what);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLocalMessenger = new Messenger(mIncomingHandler);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        mScreenWidth = dm.widthPixels;
        Log.d(TAG, "EvieOverlayService created. Screen width: " + mScreenWidth);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Launcher bound to EvieOverlayService: " + intent);
        return mLocalMessenger.getBinder();
    }

    private void handleAttachWindow(Message msg) {
        Log.d(TAG, "handleAttachWindow received");
        mLauncherMessenger = msg.replyTo;

        if (msg.obj instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams hostLp = (WindowManager.LayoutParams) msg.obj;
            IBinder hostToken = hostLp.token;
            Log.d(TAG, "Host window token: " + hostToken);

            if (hostToken != null) {
                attachShelfWindow(hostToken);
            }
        }

        // Send acknowledgment back to Launcher so it knows the overlay is connected!
        notifyLauncherServiceState(true);
    }

    private void attachShelfWindow(IBinder hostToken) {
        if (mIsAttached && mShelfView != null) {
            return;
        }

        mShelfView = new WidgetShelfView(this);
        mShelfView.setOnCloseListener(this::closeShelf);
        mShelfView.setSwipeProgressListener(progress -> {
            mLastProgress = progress;
            updateBlurForProgress(progress);
            sendScrollToLauncher(progress);
        });
        mShelfView.startListening();

        mWindowLayoutParams = new WindowManager.LayoutParams();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mWindowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            mWindowLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        mWindowLayoutParams.token = null;
        mWindowLayoutParams.format = PixelFormat.TRANSLUCENT;
        mWindowLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mWindowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        mWindowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        mWindowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        // Take space where camera notch / cutout is
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            mWindowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            mWindowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Apply authentic background wallpaper blur like launcher all-apps drawer (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            mWindowLayoutParams.setBlurBehindRadius(0);
            mWindowLayoutParams.dimAmount = 0.0f;
            mLastAppliedBlur = 0;
            mLastAppliedDim = 0.0f;
        }

        // Position offscreen to the left initially, keep VISIBLE so SurfaceControl remains allocated
        mShelfView.setTranslationX(-mScreenWidth);
        mShelfView.setAlpha(0.0f);
        mShelfView.setVisibility(View.VISIBLE);

        try {
            mWindowManager.addView(mShelfView, mWindowLayoutParams);
            mIsAttached = true;
            Log.d(TAG, "Overlay shelf window successfully added to WindowManager (TYPE_APPLICATION_OVERLAY)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay window to WindowManager", e);
            mIsAttached = false;
        }
    }

    private void cancelScrollAnimator() {
        if (mScrollAnimator != null) {
            ValueAnimator anim = mScrollAnimator;
            mScrollAnimator = null;
            anim.removeAllListeners();
            anim.removeAllUpdateListeners();
            anim.cancel();
        }
    }

    private void handleStartScroll() {
        Log.d(TAG, "handleStartScroll");
        cancelScrollAnimator();
        WidgetHostManager.ensureListening(this);
        if (mShelfView != null) {
            mShelfView.setVisibility(View.VISIBLE);
            mShelfView.reloadWidgets();
        }
        // Clear touch/focus blocking flags so WindowManager and SurfaceFlinger allow active blur-behind composition
        if (mWindowLayoutParams != null) {
            mWindowLayoutParams.alpha = 1.0f;
            mWindowLayoutParams.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }
            safeUpdateViewLayout();
        }
    }

    private void updateBlurForProgress(float p) {
        if (mWindowLayoutParams == null) return;

        boolean lpChanged = false;

        // When dragging or entering shelf (p > 0.001f), ensure window is visible, composited, and receptive to blur
        if (p > 0.001f) {
            if (mWindowLayoutParams.alpha != 1.0f) {
                mWindowLayoutParams.alpha = 1.0f;
                lpChanged = true;
            }
            int blockedFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if ((mWindowLayoutParams.flags & blockedFlags) != 0) {
                mWindowLayoutParams.flags &= ~blockedFlags;
                lpChanged = true;
            }
        }

        int quantizedBlur = 0;
        float targetDim = 0.0f;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if ((mWindowLayoutParams.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) {
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                lpChanged = true;
            }
            if ((mWindowLayoutParams.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0) {
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                lpChanged = true;
            }

            // Responsive depth curve matching Motorola Launcher: blur begins perceptibly early
            float factor = Math.min(p * 2.2f, 1.0f);
            int blur = Math.max(0, Math.min(100, Math.round(factor * 100)));
            quantizedBlur = (blur / 4) * 4;
            if (p >= 0.95f) quantizedBlur = 100;
            if (p <= 0.01f) quantizedBlur = 0;
            if (quantizedBlur != mLastAppliedBlur) {
                mLastAppliedBlur = quantizedBlur;
                mWindowLayoutParams.setBlurBehindRadius(quantizedBlur);
                lpChanged = true;
            }

            // SurfaceFlinger requires Dim Layer getAlpha() > 0 to be visible (EffectLayer::isVisible).
            // Without dimAmount, alpha is 0 and SurfaceFlinger culls the Dim Layer as "color{<0,0,0,0>}".
            // Setting a minimal dimAmount ensures SurfaceFlinger keeps the blur Dim Layer alive!
            targetDim = (quantizedBlur > 0) ? Math.max(0.01f, p * 0.12f) : 0.0f;
            targetDim = Math.round(targetDim * 100.0f) / 100.0f;
            if (Math.abs(mLastAppliedDim - targetDim) > 0.005f) {
                mLastAppliedDim = targetDim;
                mWindowLayoutParams.dimAmount = targetDim;
                lpChanged = true;
            }
        }

        if (lpChanged) {
            Log.d(TAG, "updateBlurForProgress: p=" + p + " -> appliedBlur=" + quantizedBlur + ", dim=" + targetDim + ", attached=" + (mShelfView != null && mShelfView.isAttachedToWindow()));
            safeUpdateViewLayout();
        }
    }

    private void applyShelfProgress(float p) {
        mLastProgress = p;
        if (mShelfView != null) {
            if (p > 0.001f && mShelfView.getVisibility() != View.VISIBLE) {
                mShelfView.setVisibility(View.VISIBLE);
                WidgetHostManager.ensureListening(this);
            }
            float tx = (p - 1.0f) * mScreenWidth;
            mShelfView.setTranslationX(tx);
            // Smooth fade-in / fade-out animation
            float alpha = Math.max(0.0f, Math.min(1.0f, p));
            mShelfView.setAlpha(alpha);
        }

        updateBlurForProgress(p);
    }

    private void handleUpdateScroll(float progress) {
        Log.d(TAG, "handleUpdateScroll: p=" + progress);
        if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
            cancelScrollAnimator();
        }
        applyShelfProgress(progress);
    }

    private void handleEndScroll(float velocity) {
        Log.d(TAG, "handleEndScroll: progress=" + mLastProgress + ", velocity=" + velocity);
        if (mShelfView == null || mWindowLayoutParams == null) return;

        // Fling right (positive velocity) or drag past 35% opens the shelf
        final boolean shouldOpen = (velocity > 300) || (velocity > -300 && mLastProgress > 0.35f);
        final float target = shouldOpen ? 1.0f : 0.0f;

        animateShelf(mLastProgress, target, () -> {
            if (shouldOpen) {
                mShelfView.setTranslationX(0);
                mShelfView.setAlpha(1.0f);
                // Clear touch blocking flags so shelf and widgets receive touch events!
                mWindowLayoutParams.flags &= ~(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                            | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                    mWindowLayoutParams.setBlurBehindRadius(100);
                    mWindowLayoutParams.dimAmount = 0.12f;
                    mLastAppliedBlur = 100;
                    mLastAppliedDim = 0.12f;
                }
                safeUpdateViewLayout();
                mShelfView.requestFocus();
                sendScrollToLauncher(1.0f);
            } else {
                mShelfView.setTranslationX(-mScreenWidth);
                mShelfView.setAlpha(0.0f);
                mShelfView.setVisibility(View.VISIBLE);
                mWindowLayoutParams.alpha = 1.0f;
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    mWindowLayoutParams.setBlurBehindRadius(0);
                    mWindowLayoutParams.dimAmount = 0.0f;
                    mLastAppliedBlur = 0;
                    mLastAppliedDim = 0.0f;
                }
                safeUpdateViewLayout();
                sendScrollToLauncher(0.0f);
            }
        });
    }

    public void closeShelf() {
        Log.d(TAG, "closeShelf requested");
        if (mShelfView == null || mWindowLayoutParams == null) return;

        // Immediately block touches on shelf while animating closed
        mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        safeUpdateViewLayout();

        // Compute start progress strictly from the current shelf translationX so there is ZERO jump!
        float startProgress;
        if (mScreenWidth > 0) {
            float currentTx = mShelfView.getTranslationX();
            startProgress = Math.max(0.0f, Math.min(1.0f, 1.0f + (currentTx / (float) mScreenWidth)));
        } else {
            startProgress = mLastProgress > 0 ? mLastProgress : 1.0f;
        }

        animateShelf(startProgress, 0.0f, () -> {
            if (mShelfView != null) {
                mShelfView.setTranslationX(-mScreenWidth);
                mShelfView.setAlpha(0.0f);
                mShelfView.setVisibility(View.VISIBLE);
            }
            if (mWindowLayoutParams != null) {
                mWindowLayoutParams.alpha = 1.0f;
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    mWindowLayoutParams.setBlurBehindRadius(0);
                    mWindowLayoutParams.dimAmount = 0.0f;
                    mLastAppliedBlur = 0;
                    mLastAppliedDim = 0.0f;
                }
                safeUpdateViewLayout();
            }
            sendScrollToLauncher(0.0f);
        });
    }

    private void animateShelf(float fromProgress, float toProgress, Runnable onComplete) {
        cancelScrollAnimator();

        if (Math.abs(fromProgress - toProgress) < 0.001f) {
            mLastProgress = toProgress;
            applyShelfProgress(toProgress);
            sendScrollToLauncher(toProgress);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        int duration = (int) (260 * Math.abs(fromProgress - toProgress));
        if (duration < 120) duration = 120;

        final ValueAnimator animator = ValueAnimator.ofFloat(fromProgress, toProgress);
        mScrollAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            if (mScrollAnimator == animator) {
                float p = (Float) animation.getAnimatedValue();
                applyShelfProgress(p);
                sendScrollToLauncher(p);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mIsCanceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mIsCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mIsCanceled || mScrollAnimator != animator) {
                    return;
                }
                mScrollAnimator = null;
                mLastProgress = toProgress;
                sendScrollToLauncher(toProgress);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        animator.start();
    }

    private void sendScrollToLauncher(float progress) {
        if (mLauncherMessenger != null) {
            try {
                Message msg = Message.obtain(null, MSG_UPDATE_SCROLL);
                msg.arg1 = Float.floatToIntBits(progress);
                mLauncherMessenger.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send scroll update to launcher", e);
            }
        }
    }

    private void handleDetachWindow() {
        Log.d(TAG, "handleDetachWindow");
        if (mIsAttached && mShelfView != null) {
            try {
                mShelfView.stopListening();
                if (mShelfView.isAttachedToWindow()) {
                    mWindowManager.removeView(mShelfView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing shelf view", e);
            }
            mShelfView = null;
            mIsAttached = false;
        }
    }

    private void handleHide() {
        Log.d(TAG, "handleHide");
        cancelScrollAnimator();
        mLastProgress = 0.0f;
        sendScrollToLauncher(0.0f);
        if (mShelfView != null) {
            mShelfView.setTranslationX(-mScreenWidth);
            mShelfView.setAlpha(0.0f);
            mShelfView.setVisibility(View.GONE);
            if (mWindowLayoutParams != null) {
                mWindowLayoutParams.alpha = 0.0f;
                mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    mWindowLayoutParams.setBlurBehindRadius(0);
                    mWindowLayoutParams.dimAmount = 0.0f;
                    mLastAppliedBlur = 0;
                    mLastAppliedDim = 0.0f;
                }
                safeUpdateViewLayout();
            }
        }
    }

    private void safeUpdateViewLayout() {
        if (mIsAttached && mShelfView != null && mShelfView.isAttachedToWindow()) {
            try {
                mWindowManager.updateViewLayout(mShelfView, mWindowLayoutParams);
            } catch (Exception e) {
                Log.e(TAG, "safeUpdateViewLayout error", e);
            }
        }
    }

    private void notifyLauncherServiceState(boolean attached) {
        if (mLauncherMessenger != null) {
            try {
                Message msg = Message.obtain(null, MSG_SERVICE_STATE_CHANGED);
                mLauncherMessenger.send(msg);
                Log.d(TAG, "Sent MSG_SERVICE_STATE_CHANGED (" + MSG_SERVICE_STATE_CHANGED + ") to launcher");
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to notify launcher of service state", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handleDetachWindow();
    }
}
