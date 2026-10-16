package com.discoveryscreen.ui;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.discoveryscreen.widget.WidgetHostManager;
import com.discoveryscreen.widget.WidgetStorage;
import com.discoveryscreen.overlay.EvieOverlayService;

/** Transparent activity used only to perform the system widget bind/configure result flow. */
public class WidgetAddActivity extends Activity {
    private static final String TAG = "WidgetAddActivity";
    private static final int REQUEST_BIND = 2101;
    private static final int REQUEST_CONFIGURE = 2102;

    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean finishedFlow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = WidgetHostManager.getHost(this);
        if (state != null) {
            widgetId = state.getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
            finishedFlow = state.getBoolean("finished_flow", false);
            return;
        }

        ComponentName provider = getIntent().getParcelableExtra(WidgetPickerSheet.EXTRA_PROVIDER);
        if (provider == null) {
            finishAndReturnToShelf(AppWidgetManager.INVALID_APPWIDGET_ID);
            return;
        }
        try {
            widgetId = appWidgetHost.allocateAppWidgetId();
            if (appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)) {
                configureOrSave();
            } else {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                startActivityForResult(bind, REQUEST_BIND);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to bind selected widget", e);
            fail("Could not add widget");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("widget_id", widgetId);
        outState.putBoolean("finished_flow", finishedFlow);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BIND) {
            if (resultCode == RESULT_OK) configureOrSave();
            else fail(null);
        } else if (requestCode == REQUEST_CONFIGURE) {
            if (resultCode == RESULT_OK) saveAndFinish();
            else fail(null);
        }
    }

    private void configureOrSave() {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
        if (info == null) {
            fail("Widget provider is unavailable");
            return;
        }
        if (info.configure == null) {
            saveAndFinish();
            return;
        }
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(this, widgetId, 0,
                    REQUEST_CONFIGURE, null);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open widget configuration", e);
            fail("Could not configure widget");
        }
    }

    private void saveAndFinish() {
        if (finishedFlow) return;
        finishedFlow = true;
        int addedWidgetId = widgetId;
        WidgetStorage.addWidgetId(this, addedWidgetId);
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        finishAndReturnToShelf(addedWidgetId);
    }

    private void fail(String message) {
        if (finishedFlow) return;
        finishedFlow = true;
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try { appWidgetHost.deleteAppWidgetId(widgetId); }
            catch (Exception ignored) { }
            widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        }
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finishAndReturnToShelf(AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    private void finishAndReturnToShelf(int addedWidgetId) {
        try {
            Intent reopen = new Intent(this, EvieOverlayService.class);
            reopen.setAction(EvieOverlayService.ACTION_REOPEN_AFTER_WIDGET_ADD);
            reopen.putExtra(EvieOverlayService.EXTRA_ADDED_WIDGET_ID, addedWidgetId);
            startService(reopen);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to reopen widget shelf", e);
        }
        overridePendingTransition(0, 0);
        finish();
    }
}
