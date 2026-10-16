package com.discoveryscreen.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Moto-inspired, searchable catalog of installed home-screen widgets. */
public class WidgetPickerActivity extends Activity {
    public static final String EXTRA_PROVIDER = "com.discoveryscreen.extra.WIDGET_PROVIDER";

    private final List<AppWidgetProviderInfo> providers = new ArrayList<>();
    private LinearLayout rows;
    private EditText search;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(24, 24, 27));
        getWindow().setNavigationBarColor(Color.rgb(24, 24, 27));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(24, 24, 27));
        root.setPadding(dp(20), dp(16), dp(20), 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Widgets", 25, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView close = text("✕", 22, Color.LTGRAY, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close widget picker");
        close.setOnClickListener(v -> finish());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(titleRow);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(166, 166, 175));
        search.setHint("Search widgets");
        search.setCompoundDrawablePadding(dp(10));
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(roundRect(Color.rgb(48, 48, 53), dp(18)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(50));
        searchLp.topMargin = dp(10);
        root.addView(search, searchLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(14), 0, dp(24));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        empty = text("No widgets found", 15, Color.rgb(166, 166, 175), false);
        empty.setGravity(Gravity.CENTER);

        loadProviders();
        render("");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadProviders() {
        try {
            providers.addAll(AppWidgetManager.getInstance(this).getInstalledProviders());
        } catch (RuntimeException ignored) { }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(providers, new Comparator<AppWidgetProviderInfo>() {
            @Override public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                int appOrder = collator.compare(appName(a), appName(b));
                return appOrder != 0 ? appOrder : collator.compare(widgetName(a), widgetName(b));
            }
        });
    }

    private void render(String query) {
        rows.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<AppWidgetProviderInfo> matches = new ArrayList<>();
        for (AppWidgetProviderInfo info : providers) {
            String haystack = (appName(info) + " " + widgetName(info) + " " + info.provider.getPackageName())
                    .toLowerCase(Locale.getDefault());
            if (needle.isEmpty() || haystack.contains(needle)) matches.add(info);
        }

        if (matches.isEmpty()) {
            rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(220)));
            return;
        }

        String currentApp = null;
        LinearLayout grid = null;
        int itemInApp = 0;
        for (AppWidgetProviderInfo info : matches) {
            String app = appName(info);
            if (!app.equals(currentApp)) {
                currentApp = app;
                itemInApp = 0;
                TextView heading = text(app, 16, Color.WHITE, true);
                LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
                headingLp.topMargin = dp(14);
                headingLp.bottomMargin = dp(10);
                rows.addView(heading, headingLp);
                grid = new LinearLayout(this);
                grid.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(grid, new LinearLayout.LayoutParams(-1, -2));
            }
            if (itemInApp > 0 && itemInApp % 2 == 0) {
                grid = new LinearLayout(this);
                grid.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.topMargin = dp(10);
                rows.addView(grid, rowLp);
            }
            addWidgetCard(grid, info, itemInApp % 2 == 0);
            itemInApp++;
        }
        if (itemInApp % 2 == 1) {
            grid.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    private void addWidgetCard(LinearLayout row, AppWidgetProviderInfo info, boolean left) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(12));
        card.setBackground(roundRect(Color.rgb(39, 39, 43), dp(20)));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> select(info));

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = null;
        try { drawable = info.loadPreviewImage(this, getResources().getDisplayMetrics().densityDpi); }
        catch (RuntimeException ignored) { }
        if (drawable == null) {
            try { drawable = info.loadIcon(this, getResources().getDisplayMetrics().densityDpi); }
            catch (RuntimeException ignored) { }
        }
        if (drawable != null) preview.setImageDrawable(drawable);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(132));
        card.addView(preview, previewLp);

        TextView name = text(widgetName(info), 14, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(8);
        card.addView(name, nameLp);

        TextView dimensions = text(dimensions(info), 12, Color.rgb(170, 170, 178), false);
        dimensions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dimensionsLp = new LinearLayout.LayoutParams(-1, -2);
        dimensionsLp.topMargin = dp(3);
        card.addView(dimensions, dimensionsLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
        if (left) cardLp.rightMargin = dp(7);
        else cardLp.leftMargin = dp(7);
        row.addView(card, cardLp);
    }

    private void select(AppWidgetProviderInfo info) {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
        Intent result = new Intent();
        result.putExtra(EXTRA_PROVIDER, info.provider);
        setResult(RESULT_OK, result);
        finish();
    }

    private String appName(AppWidgetProviderInfo info) {
        try { return String.valueOf(getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(info.provider.getPackageName(), 0))); }
        catch (Exception ignored) { return info.provider.getPackageName(); }
    }

    private String widgetName(AppWidgetProviderInfo info) {
        try {
            CharSequence label = info.loadLabel(getPackageManager());
            if (label != null && label.length() > 0) return label.toString();
        } catch (RuntimeException ignored) { }
        return info.provider.getClassName();
    }

    private String dimensions(AppWidgetProviderInfo info) {
        int minW = Math.max(1, Math.round(info.minWidth / getResources().getDisplayMetrics().density));
        int minH = Math.max(1, Math.round(info.minHeight / getResources().getDisplayMetrics().density));
        return "min " + minW + " × " + minH + " dp";
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
