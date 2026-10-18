package com.discoveryscreen.ui;

import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.discoveryscreen.widget.WidgetHostManager;
import com.discoveryscreen.widget.WidgetSizing;
import com.discoveryscreen.widget.WidgetStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PICK_WIDGET = 1001;
    private static final int REQUEST_CREATE_WIDGET = 1002;
    private static final int REQUEST_BIND_WIDGET = 1003;

    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;
    private LinearLayout mWidgetsPreviewContainer;
    private TextView mTvNoWidgets;
    private int mPendingWidgetId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier("activity_main", "layout", getPackageName()));
        // targetSdk 36 draws edge-to-edge, so apply the system bar insets here.
        EdgeToEdge.padFor(findViewById(android.R.id.content), WindowInsets.Type.systemBars());

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = WidgetHostManager.getHost(this);

        int containerId = getResources().getIdentifier("widgets_preview_container", "id", getPackageName());
        int noWidgetsId = getResources().getIdentifier("tv_no_widgets", "id", getPackageName());
        int btnAddId = getResources().getIdentifier("btn_add_widget", "id", getPackageName());
        int btnPreviewId = getResources().getIdentifier("btn_preview", "id", getPackageName());
        int btnSettingsId = getResources().getIdentifier("btn_open_launcher_settings", "id", getPackageName());
        int btnLauncherInfoId = getResources().getIdentifier("btn_launcher_app_info", "id", getPackageName());

        mWidgetsPreviewContainer = findViewById(containerId);
        mTvNoWidgets = findViewById(noWidgetsId);

        Button btnAdd = findViewById(btnAddId);
        Button btnPreview = findViewById(btnPreviewId);
        Button btnSettings = findViewById(btnSettingsId);
        Button btnLauncherInfo = findViewById(btnLauncherInfoId);

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> launchWidgetPicker());
        }

        if (btnPreview != null) {
            btnPreview.setOnClickListener(v -> showPreviewDialog());
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> openLauncherSettings());
        }
        if (btnLauncherInfo != null) {
            btnLauncherInfo.setOnClickListener(v -> openLauncherAppInfo());
        }

        if ("pick_widget".equals(getIntent().getStringExtra("action"))) {
            launchWidgetPicker();
        }

        checkOverlayPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        int cardPermissionId = getResources().getIdentifier("card_permission", "id", getPackageName());
        int btnGrantId = getResources().getIdentifier("btn_grant_overlay", "id", getPackageName());
        View cardPermission = findViewById(cardPermissionId);
        Button btnGrant = findViewById(btnGrantId);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                if (cardPermission != null) cardPermission.setVisibility(View.VISIBLE);
                if (btnGrant != null) {
                    btnGrant.setOnClickListener(v -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    });
                }
            } else {
                if (cardPermission != null) cardPermission.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        WidgetHostManager.ensureListening(this);
        refreshWidgetsList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Do not stop host listening here so EvieOverlayService keeps receiving widget updates!
    }

    private void launchWidgetPicker() {
        try {
            startActivityForResult(new Intent(this, WidgetPickerActivity.class), REQUEST_PICK_WIDGET);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch widget picker", e);
            Toast.makeText(this, "Could not open widget picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_WIDGET) {
            if (resultCode == RESULT_OK && data != null) {
                ComponentName provider = data.getParcelableExtra(WidgetPickerActivity.EXTRA_PROVIDER);
                if (provider != null) bindAndAddWidget(provider);
            }
            return;
        }

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_BIND_WIDGET) {
                configureOrAddWidget(mPendingWidgetId);
            } else if (requestCode == REQUEST_CREATE_WIDGET) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingWidgetId) : mPendingWidgetId;
                saveAndDisplayWidget(widgetId);
            }
        } else if (resultCode == RESULT_CANCELED) {
            if (mPendingWidgetId != -1) {
                mAppWidgetHost.deleteAppWidgetId(mPendingWidgetId);
                mPendingWidgetId = -1;
            }
        }
    }

    private void bindAndAddWidget(ComponentName provider) {
        try {
            mPendingWidgetId = mAppWidgetHost.allocateAppWidgetId();
            if (mAppWidgetManager.bindAppWidgetIdIfAllowed(mPendingWidgetId, provider)) {
                configureOrAddWidget(mPendingWidgetId);
                return;
            }

            Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingWidgetId);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
            startActivityForResult(bindIntent, REQUEST_BIND_WIDGET);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind selected widget", e);
            if (mPendingWidgetId != -1) mAppWidgetHost.deleteAppWidgetId(mPendingWidgetId);
            mPendingWidgetId = -1;
            Toast.makeText(this, "Could not add widget: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void configureOrAddWidget(int widgetId) {
        AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(widgetId);
        if (info == null) {
            return;
        }

        if (info.configure != null) {
            try {
                // Official Android way to launch widget configure activity (supports unexported activities)
                mAppWidgetHost.startAppWidgetConfigureActivityForResult(
                    this, widgetId, 0, REQUEST_CREATE_WIDGET, null
                );
            } catch (Exception e) {
                Log.w(TAG, "Could not launch widget configuration activity via AppWidgetHost", e);
                try {
                    Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                    intent.setComponent(info.configure);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                    startActivityForResult(intent, REQUEST_CREATE_WIDGET);
                } catch (Exception ex) {
                    Log.w(TAG, "Direct configure launch also failed, saving widget directly", ex);
                    saveAndDisplayWidget(widgetId);
                }
            }
        } else {
            saveAndDisplayWidget(widgetId);
        }
    }

    private void saveAndDisplayWidget(int widgetId) {
        WidgetStorage.addWidgetId(this, widgetId);
        mPendingWidgetId = -1;
        refreshWidgetsList();
        Toast.makeText(this, "Widget added to shelf!", Toast.LENGTH_SHORT).show();
    }

    private void refreshWidgetsList() {
        if (mWidgetsPreviewContainer == null) return;

        List<Integer> storedIds = WidgetStorage.getWidgetIds(this);
        Set<Integer> alive = new HashSet<>();
        for (int id : storedIds) {
            try {
                if (mAppWidgetManager.getAppWidgetInfo(id) != null) {
                    alive.add(id);
                }
            } catch (Exception ignored) {}
        }
        List<Integer> widgetIds = WidgetStorage.pruneStaleWidgets(this, alive);

        mWidgetsPreviewContainer.removeAllViews();
        if (widgetIds.isEmpty()) {
            if (mTvNoWidgets != null) {
                mWidgetsPreviewContainer.addView(mTvNoWidgets);
            }
            return;
        }

        int cardBgId = getResources().getIdentifier("card_background", "drawable", getPackageName());

        for (int i = 0; i < widgetIds.size(); i++) {
            final int pos = i;
            final int widgetId = widgetIds.get(i);
            AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(widgetId);
            if (info == null) continue;

            int heightDp = WidgetStorage.getWidgetHeightDp(this, widgetId);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(cardBgId);
            card.setPadding(16, 16, 16, 16);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);

            // Controls row
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setPadding(0, 0, 0, 10);

            // Up button
            Button btnUp = new Button(this);
            btnUp.setText("▲");
            btnUp.setTextSize(13);
            btnUp.setBackground(null);
            btnUp.setTextColor(pos > 0 ? Color.WHITE : Color.parseColor("#44FFFFFF"));
            btnUp.setEnabled(pos > 0);
            btnUp.setOnClickListener(v -> {
                WidgetStorage.moveWidget(this, pos, pos - 1);
                refreshWidgetsList();
            });
            topRow.addView(btnUp);

            // Down button
            Button btnDown = new Button(this);
            btnDown.setText("▼");
            btnDown.setTextSize(13);
            btnDown.setBackground(null);
            btnDown.setTextColor(pos < widgetIds.size() - 1 ? Color.WHITE : Color.parseColor("#44FFFFFF"));
            btnDown.setEnabled(pos < widgetIds.size() - 1);
            btnDown.setOnClickListener(v -> {
                WidgetStorage.moveWidget(this, pos, pos + 1);
                refreshWidgetsList();
            });
            topRow.addView(btnDown);

            // Size button
            Button btnSize = new Button(this);
            btnSize.setText(heightDp <= 0 ? "Size: Auto" : "Size: " + heightDp + "dp");
            btnSize.setTextSize(12);
            btnSize.setBackground(null);
            btnSize.setTextColor(MonetColors.getAccentColor(this));
            btnSize.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            btnSize.setOnClickListener(v -> {
                WidgetStorage.cycleWidgetHeight(this, widgetId);
                refreshWidgetsList();
            });
            topRow.addView(btnSize);

            // Remove button
            Button btnRemove = new Button(this);
            btnRemove.setText("✕ Remove");
            btnRemove.setTextColor(Color.parseColor("#FF453A"));
            btnRemove.setTextSize(12);
            btnRemove.setBackground(null);
            btnRemove.setOnClickListener(v -> {
                mAppWidgetHost.deleteAppWidgetId(widgetId);
                WidgetStorage.removeWidgetId(this, widgetId);
                refreshWidgetsList();
            });
            topRow.addView(btnRemove);

            card.addView(topRow);

            try {
                AppWidgetHostView widgetView = mAppWidgetHost.createView(this, widgetId, info);

                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int screenWidthDp = Math.max(280, (int) (dm.widthPixels / dm.density) - 48);
                int effectiveHeightDp = heightDp > 0 ? heightDp : (info.minHeight > 0 ? info.minHeight : 150);
                try {
                    WidgetSizing.updateHostViewSize(this, widgetView, info,
                            screenWidthDp, effectiveHeightDp);
                } catch (Exception ignored) {}

                if (heightDp > 0) {
                    int heightPx = (int) (heightDp * dm.density + 0.5f);
                    card.addView(widgetView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        heightPx
                    ));
                } else {
                    card.addView(widgetView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error displaying widget: " + widgetId, e);
            }

            mWidgetsPreviewContainer.addView(card);
        }
    }

    private void showPreviewDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        WidgetShelfView shelfView = new WidgetShelfView(this);
        shelfView.startListening();

        dialog.setContentView(shelfView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    && BlurSupport.isCrossWindowBlurEnabled(this)) {
                try {
                    window.setBackgroundBlurRadius(
                            com.discoveryscreen.overlay.EvieProtocol.MAX_BLUR_RADIUS);
                    window.setDimAmount(0.12f);
                } catch (Exception ignored) {
                }
            }
        }
        dialog.setOnDismissListener(d -> shelfView.stopListening());
        dialog.show();
    }

    private void openLauncherSettings() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("evie_overlay_prefs", MODE_PRIVATE);
        String uri = prefs.getString("settings_intent_uri", null);
        if (uri != null && !uri.isEmpty()) {
            try {
                Intent stored = Intent.parseUri(uri, 0);
                stored.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(stored);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent intent = new Intent("android.intent.action.APPLICATION_PREFERENCES");
            intent.setPackage("com.motorola.launcher3");
            startActivity(intent);
        } catch (Exception e1) {
            openLauncherAppInfo();
        }
    }

    private void openLauncherAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:com.motorola.launcher3"));
            startActivity(intent);
        } catch (Exception e1) {
            try {
                // Keep a useful destination if the Motorola launcher package
                // is unavailable on this device.
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "Please open Home settings on your home screen", Toast.LENGTH_LONG).show();
            }
        }
    }
}
