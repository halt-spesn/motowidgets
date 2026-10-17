package com.discoveryscreen.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.animation.ValueAnimator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.LayoutTransition;
import com.discoveryscreen.R;
import com.discoveryscreen.widget.Interpolators;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Floating, searchable widget catalog shown over the open shelf. */
final class WidgetPickerSheet extends FrameLayout {
    static final String EXTRA_PROVIDER = "com.discoveryscreen.extra.WIDGET_PROVIDER";

    interface Listener {
        void onWidgetSelected(ComponentName provider);
        void onDismissed();
        void onBlurProgress(float progress);
    }

    private final Listener listener;
    private final List<AppWidgetProviderInfo> providers = new ArrayList<>();
    private final Map<String, String> appNames = new HashMap<>();
    private final Map<ComponentName, String> widgetNames = new HashMap<>();
    private final Map<ComponentName, String> widgetDescriptions = new HashMap<>();
    private final Map<ComponentName, int[]> widgetSpanCache = new HashMap<>();
    private LinearLayout rows;
    private final EditText search;
    private final TextView empty;
    private final ScrollView scroll;
    private LinearLayout frostedPanel;
    private View bottomSpacer;
    private int navigationBarInset;
    private int statusBarInset;
    private boolean dismissing;
    private Runnable afterDismiss;
    private int openAnimationGeneration;
    private ValueAnimator panelHeightAnimator;
    private AnimatorSet panelOpenAnimator;
    private ValueAnimator blurProgressAnimator;
    private float pickerBlurProgress;
    private int availableHeight;
    private int collapsedHeight;
    private int expandedHeight;
    private float dragStartY;
    private float dragStartTranslationY;
    private int dragStartHeight;
    private boolean draggingSheet;
    private String expandedPackage;
    private final List<LazyDrawable> lazyDrawables = new ArrayList<>();
    private int renderGeneration;
    private boolean visibleDrawableLoadScheduled;
    private int providerRefreshGeneration;
    private boolean providerDataLoaded;
    private final int accentColor;
    private final int glassColor;
    private final int glassStrokeColor;
    private final int primaryTextColor;
    private final int secondaryTextColor;

    private interface DrawableLoader { Drawable load() throws Exception; }

    private static final class LazyDrawable {
        final ImageView view;
        final int generation;
        final DrawableLoader loader;
        boolean started;

        LazyDrawable(ImageView view, int generation, DrawableLoader loader) {
            this.view = view;
            this.generation = generation;
            this.loader = loader;
        }
    }

    private static final ExecutorService PREVIEW_LOADER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "widget-picker-preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService PROVIDER_LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "widget-picker-providers");
        thread.setDaemon(true);
        return thread;
    });

    private static final class ProviderSnapshot {
        final List<AppWidgetProviderInfo> providers;
        final Map<String, String> appNames;
        final Map<ComponentName, String> widgetNames;
        final Map<ComponentName, String> widgetDescriptions;
        final Map<ComponentName, int[]> widgetSpans;

        ProviderSnapshot(List<AppWidgetProviderInfo> providers, Map<String, String> appNames,
                         Map<ComponentName, String> widgetNames,
                         Map<ComponentName, String> widgetDescriptions,
                         Map<ComponentName, int[]> widgetSpans) {
            this.providers = providers;
            this.appNames = appNames;
            this.widgetNames = widgetNames;
            this.widgetDescriptions = widgetDescriptions;
            this.widgetSpans = widgetSpans;
        }
    }

    WidgetPickerSheet(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        accentColor = MonetColors.getAccentColor(context);
        // Motorola's dark widget picker uses #99333333 for search and list surfaces.
        glassColor = 0x99333333;
        glassStrokeColor = Color.TRANSPARENT;
        primaryTextColor = MonetColors.getNeutralTextColor(context);
        secondaryTextColor = context.getColor(R.color.text_secondary);
        setClickable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(0x44000000);
        setOnClickListener(v -> dismiss());
        setOnApplyWindowInsetsListener((view, insets) -> {
            int bottomInset = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    ? insets.getTappableElementInsets().bottom
                    : insets.getSystemWindowInsetBottom();
            if (navigationBarInset != bottomInset) {
                navigationBarInset = bottomInset;
                if (bottomSpacer != null) {
                    ViewGroup.LayoutParams spacerLp = bottomSpacer.getLayoutParams();
                    spacerLp.height = dp(24) + navigationBarInset;
                    bottomSpacer.setLayoutParams(spacerLp);
                }
                invalidate();
                requestLayout();
            }
            int topInset = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
            if (statusBarInset != topInset) {
                statusBarInset = topInset;
                updatePanelHeight();
            }
            return insets;
        });

        LinearLayout sheet = new LinearLayout(context);
        frostedPanel = sheet;
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), 0, dp(20), 0);
        sheet.setBackground(frostedSurface());
        sheet.setClipToOutline(true);
        sheet.setElevation(dp(20));
        sheet.setOnClickListener(v -> { });

        FrameLayout handleArea = new FrameLayout(context);
        handleArea.setContentDescription("Drag up to expand or down to close widget picker");
        handleArea.setFocusable(true);
        View handle = new View(context);
        int handleColor = systemColor(context, "system_neutral2_300", 0xFFB9B9C0);
        handle.setBackground(roundRect(handleColor, dp(3), 0x00000000));
        FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(dp(40), dp(6),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        handleLp.topMargin = dp(16);
        handleArea.addView(handle, handleLp);
        sheet.addView(handleArea, new LinearLayout.LayoutParams(-1, dp(44)));

        FrameLayout titleRow = new FrameLayout(context);
        TextView title = text("Widgets", 24, primaryTextColor, false);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new FrameLayout.LayoutParams(-1, dp(40)));
        sheet.addView(titleRow);

        View.OnTouchListener dragListener = this::handleSheetDrag;
        handleArea.setOnTouchListener(dragListener);
        titleRow.setOnTouchListener(dragListener);

        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setPadding(dp(14), 0, dp(12), 0);
        searchBox.setBackground(roundRect(glassColor, dp(24), glassStrokeColor));
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_widget_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(secondaryTextColor));
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(28), dp(48)));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTextColor(primaryTextColor);
        search.setHintTextColor(secondaryTextColor);
        search.setCursorVisible(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(primaryTextColor);
            cursor.setSize(dp(2), dp(24));
            search.setTextCursorDrawable(cursor);
        }
        search.setHint("Search widgets");
        search.setPadding(dp(4), 0, dp(4), 0);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.topMargin = dp(14);
        searchLp.bottomMargin = dp(8);
        sheet.addView(searchBox, searchLp);

        scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(2), 0, 0);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.topMargin = dp(4);
        sheet.addView(scroll, scrollLp);
        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) ->
                scheduleVisibleDrawableLoad());
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(this::scheduleVisibleDrawableLoad);

        empty = text("Loading widgets…", 15, 0xFFD0D0D7, false);
        empty.setGravity(Gravity.CENTER);

        // Motorola opens this picker as a full-screen sheet from the start.
        availableHeight = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams sheetLp =
                new FrameLayout.LayoutParams(-1, availableHeight, Gravity.BOTTOM);
        addView(sheet, sheetLp);
        updatePanelHeight();

        render("");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private boolean handleSheetDrag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                draggingSheet = true;
                dragStartY = event.getRawY();
                dragStartTranslationY = frostedPanel.getTranslationY();
                ViewGroup.LayoutParams lp = frostedPanel.getLayoutParams();
                dragStartHeight = lp != null ? lp.height : collapsedHeight;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!draggingSheet) return false;
                float delta = event.getRawY() - dragStartY;
                int targetHeight = Math.max(collapsedHeight,
                        Math.min(expandedHeight, Math.round(dragStartHeight - delta)));
                setPanelHeight(targetHeight);
                if (dragStartHeight <= collapsedHeight && delta > 0) {
                    frostedPanel.setTranslationY(dragStartTranslationY + delta);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!draggingSheet) return false;
                draggingSheet = false;
                float moved = event.getRawY() - dragStartY;
                int currentHeight = frostedPanel.getLayoutParams().height;
                if (dragStartHeight <= collapsedHeight && moved > dp(120)) {
                    dismiss();
                } else {
                    int settleHeight = currentHeight >= (collapsedHeight + expandedHeight) / 2
                            ? expandedHeight : collapsedHeight;
                    animatePanelToHeight(settleHeight);
                }
                return true;
            default:
                return true;
        }
    }

    private void setPanelHeight(int height) {
        ViewGroup.LayoutParams lp = frostedPanel.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            frostedPanel.setLayoutParams(lp);
        }
    }

    private void animatePanelToHeight(int height) {
        if (panelHeightAnimator != null) panelHeightAnimator.cancel();
        int from = frostedPanel.getLayoutParams().height;
        ValueAnimator animator = ValueAnimator.ofInt(from, height);
        panelHeightAnimator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> setPanelHeight((Integer) value.getAnimatedValue()));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (panelHeightAnimator == animator) panelHeightAnimator = null;
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                if (panelHeightAnimator == animator) panelHeightAnimator = null;
            }
        });
        animator.start();
        frostedPanel.animate().translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updatePanelHeight();
    }

    /** Keeps the panel below the status bar, with the same 8dp gap as the shelf header. */
    private void updatePanelHeight() {
        if (frostedPanel == null) return;
        int offset = statusBarInset == 0 ? 0 : statusBarInset + dp(8);
        int base = getHeight() > 0 ? getHeight() : availableHeight;
        int height = Math.max(0, base - offset);
        if (collapsedHeight == height && expandedHeight == height) return;
        // The old height target is stale after a resize. Leave the slide animation alone; layout can
        // run while the sheet is opening.
        if (panelHeightAnimator != null) panelHeightAnimator.cancel();
        collapsedHeight = height;
        expandedHeight = height;
        setPanelHeight(height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    private ProviderSnapshot loadProviderSnapshot() {
        Context context = getContext().getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        List<AppWidgetProviderInfo> installedProviders = new ArrayList<>();
        try {
            List<AppWidgetProviderInfo> installed =
                    AppWidgetManager.getInstance(context).getInstalledProviders();
            if (installed != null) {
                for (AppWidgetProviderInfo info : installed) {
                    if (info != null && info.provider != null) installedProviders.add(info);
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w("WidgetPickerSheet", "Unable to query installed widgets", e);
            return null;
        }

        Map<String, String> loadedAppNames = new HashMap<>();
        Map<ComponentName, String> loadedWidgetNames = new HashMap<>();
        Map<ComponentName, String> loadedDescriptions = new HashMap<>();
        Map<ComponentName, int[]> loadedSpans = new HashMap<>();
        float density = context.getResources().getDisplayMetrics().density;
        int widthPixels = context.getResources().getDisplayMetrics().widthPixels;
        for (AppWidgetProviderInfo info : installedProviders) {
            String packageName = info.provider.getPackageName();
            if (!loadedAppNames.containsKey(packageName)) {
                String label = packageName;
                try {
                    label = String.valueOf(packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)));
                } catch (Exception ignored) { }
                loadedAppNames.put(packageName, label);
            }

            String widgetLabel = info.provider.getClassName();
            try {
                CharSequence label = info.loadLabel(packageManager);
                if (label != null && label.length() > 0) widgetLabel = label.toString();
            } catch (RuntimeException ignored) { }
            loadedWidgetNames.put(info.provider, widgetLabel);

            String description = "";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try {
                    CharSequence label = info.loadDescription(context);
                    if (label != null) description = label.toString().trim();
                } catch (RuntimeException ignored) { }
            }
            loadedDescriptions.put(info.provider, description);
            loadedSpans.put(info.provider, calculateWidgetSpans(info, density, widthPixels));
        }

        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(installedProviders, new Comparator<AppWidgetProviderInfo>() {
            @Override public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                int app = collator.compare(loadedAppNames.get(a.provider.getPackageName()),
                        loadedAppNames.get(b.provider.getPackageName()));
                if (app != 0) return app;
                int packageOrder = a.provider.getPackageName().compareTo(b.provider.getPackageName());
                if (packageOrder != 0) return packageOrder;
                int[] aSpans = loadedSpans.get(a.provider);
                int[] bSpans = loadedSpans.get(b.provider);
                int heightOrder = Integer.compare(aSpans[1], bSpans[1]);
                if (heightOrder != 0) return heightOrder;
                int widthOrder = Integer.compare(aSpans[0], bSpans[0]);
                return widthOrder != 0 ? widthOrder
                        : collator.compare(loadedWidgetNames.get(a.provider), loadedWidgetNames.get(b.provider));
            }
        });
        return new ProviderSnapshot(installedProviders, loadedAppNames, loadedWidgetNames,
                loadedDescriptions, loadedSpans);
    }

    private void render(String query) {
        renderGeneration++;
        lazyDrawables.clear();
        rows.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<AppWidgetProviderInfo> matches = new ArrayList<>();
        for (AppWidgetProviderInfo info : providers) {
            String words = (appName(info) + " " + widgetName(info) + " "
                    + widgetDescription(info) + " " + info.provider.getPackageName())
                    .toLowerCase(Locale.getDefault());
            if (needle.isEmpty() || words.contains(needle)) matches.add(info);
        }
        if (matches.isEmpty()) {
            empty.setText(providerDataLoaded ? "No widgets found" : "Loading widgets…");
            rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
            return;
        }

        List<String> packageOrder = new ArrayList<>();
        Map<String, Integer> widgetCounts = new HashMap<>();
        Map<String, List<AppWidgetProviderInfo>> widgetsByPackage = new HashMap<>();
        for (AppWidgetProviderInfo info : matches) {
            String packageName = info.provider.getPackageName();
            Integer count = widgetCounts.get(packageName);
            widgetCounts.put(packageName, count == null ? 1 : count + 1);
            List<AppWidgetProviderInfo> packageWidgets = widgetsByPackage.get(packageName);
            if (packageWidgets == null) {
                packageWidgets = new ArrayList<>();
                widgetsByPackage.put(packageName, packageWidgets);
            }
            packageWidgets.add(info);
            if (packageOrder.isEmpty() || !packageOrder.get(packageOrder.size() - 1).equals(packageName)) {
                packageOrder.add(packageName);
            }
        }

        boolean expandedPackageStillExists = false;
        if (needle.isEmpty()) {
            for (AppWidgetProviderInfo match : matches) {
                if (match.provider.getPackageName().equals(expandedPackage)) {
                    expandedPackageStillExists = true;
                    break;
                }
            }
        } else {
            // Search results must be immediately visible; the previous port left them collapsed.
            expandedPackage = matches.get(0).provider.getPackageName();
        }
        if (needle.isEmpty() && !expandedPackageStillExists) expandedPackage = null;

        final Map<String, LinearLayout> groupsByPackage = new HashMap<>();
        final Map<String, ImageView> indicatorsByPackage = new HashMap<>();
        final Map<String, View> headersByPackage = new HashMap<>();
        final Map<String, Boolean> populatedByPackage = new HashMap<>();
        LinearLayout sections = new LinearLayout(getContext());
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setBackgroundColor(Color.TRANSPARENT);
        rows.addView(sections, new LinearLayout.LayoutParams(-1, -2));
        bottomSpacer = new View(getContext());
        rows.addView(bottomSpacer, new LinearLayout.LayoutParams(-1,
                dp(24) + navigationBarInset));
        for (int sectionIndex = 0; sectionIndex < packageOrder.size(); sectionIndex++) {
            String packageName = packageOrder.get(sectionIndex);
            AppWidgetProviderInfo info = widgetsByPackage.get(packageName).get(0);
            boolean firstSection = sectionIndex == 0;
            boolean lastSection = sectionIndex == packageOrder.size() - 1;
                if (!firstSection) {
                    sections.addView(new View(getContext()),
                            new LinearLayout.LayoutParams(-1, dp(2)));
                }
                LinearLayout section = new LinearLayout(getContext());
                section.setOrientation(LinearLayout.VERTICAL);
                section.setBackground(appSectionSurface(firstSection, lastSection));
                section.setClipToOutline(true);
                LayoutTransition sectionTransitions = new LayoutTransition();
                sectionTransitions.setDuration(LayoutTransition.APPEARING, 180);
                sectionTransitions.setDuration(LayoutTransition.DISAPPEARING, 160);
                sectionTransitions.setDuration(LayoutTransition.CHANGING, 180);
                sectionTransitions.setAnimateParentHierarchy(false);
                section.setLayoutTransition(sectionTransitions);
                sections.addView(section, new LinearLayout.LayoutParams(-1, -2));

                LinearLayout header = new LinearLayout(getContext());
                header.setGravity(Gravity.CENTER_VERTICAL);
                header.setMinimumHeight(dp(88));
                header.setPadding(0, dp(20), 0, dp(20));
                ImageView appIcon = new ImageView(getContext());
                appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                appIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
                requestDrawable(appIcon,
                        () -> getContext().getPackageManager().getApplicationIcon(packageName));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
                iconLp.setMargins(dp(16), 0, dp(16), 0);
                header.addView(appIcon, iconLp);
                LinearLayout labels = new LinearLayout(getContext());
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView appTitle = text(appName(info), 16, primaryTextColor, true);
                appTitle.setSingleLine(true);
                appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(appTitle);
                int count = widgetCounts.get(packageName);
                TextView subtitle = text(count + (count == 1 ? " widget" : " widgets"),
                        14, secondaryTextColor, false);
                subtitle.setSingleLine(true);
                subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(subtitle);
                header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                ImageView expand = new ImageView(getContext());
                expand.setImageResource(R.drawable.ic_widgets_expand);
                expand.setImageTintList(ColorStateList.valueOf(secondaryTextColor));
                expand.setContentDescription("Expand widgets");
                LinearLayout.LayoutParams expandLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                expandLp.setMargins(0, 0, dp(16), 0);
                header.addView(expand, expandLp);
                section.addView(header);

                LinearLayout widgetGroup = new LinearLayout(getContext());
                widgetGroup.setOrientation(LinearLayout.VERTICAL);
                widgetGroup.setPadding(dp(11), 0, dp(11), dp(8));
                widgetGroup.setBackgroundColor(Color.TRANSPARENT);
                boolean expanded = packageName.equals(expandedPackage);
                widgetGroup.setVisibility(expanded ? VISIBLE : GONE);
                populatedByPackage.put(packageName, expanded);
                expand.setRotation(expanded ? 180f : 0f);
                header.setBackground(headerRipple(firstSection, lastSection, expanded));
                groupsByPackage.put(packageName, widgetGroup);
                indicatorsByPackage.put(packageName, expand);
                headersByPackage.put(packageName, header);
                LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(-1, -2);
                section.addView(widgetGroup, groupLp);
                if (expanded) populateWidgetGroup(widgetGroup, widgetsByPackage.get(packageName));
                final String headerPackage = packageName;
                header.setClickable(true);
                header.setFocusable(true);
                header.setContentDescription(appName(info) + ", " + subtitle.getText()
                        + (expanded ? ", expanded" : ", collapsed"));
                header.setOnClickListener(v -> {
                    boolean collapse = headerPackage.equals(expandedPackage)
                            && groupsByPackage.get(headerPackage).getVisibility() == VISIBLE;
                    expandedPackage = collapse ? null : headerPackage;
                    for (Map.Entry<String, LinearLayout> group : groupsByPackage.entrySet()) {
                        boolean show = !collapse && group.getKey().equals(expandedPackage);
                        if (show && !Boolean.TRUE.equals(populatedByPackage.get(group.getKey()))) {
                            populateWidgetGroup(group.getValue(), widgetsByPackage.get(group.getKey()));
                            populatedByPackage.put(group.getKey(), true);
                        }
                        group.getValue().setVisibility(show ? VISIBLE : GONE);
                        ImageView indicator = indicatorsByPackage.get(group.getKey());
                        indicator.animate().rotation(show ? 180f : 0f).setDuration(180)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        View sectionHeader = headersByPackage.get(group.getKey());
                        if (sectionHeader != null) {
                            int groupIndex = packageOrder.indexOf(group.getKey());
                            boolean groupIsFirst = groupIndex == 0;
                            boolean groupIsLast = groupIndex == packageOrder.size() - 1;
                            applyHeaderSurface(sectionHeader, groupIsFirst, groupIsLast, show);
                            int appWidgetCount = widgetCounts.get(group.getKey());
                            sectionHeader.setContentDescription(appNameForPackage(
                                    matches, group.getKey()) + ", " + appWidgetCount
                                    + (appWidgetCount == 1 ? " widget, " : " widgets, ")
                                    + (show ? "expanded" : "collapsed"));
                        }
                    }
                });
        }
        rows.post(this::scheduleVisibleDrawableLoad);
    }

    private void populateWidgetGroup(LinearLayout widgetGroup,
                                     List<AppWidgetProviderInfo> widgets) {
        LinearLayout row = null;
        for (int i = 0; i < widgets.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(getContext());
                row.setGravity(Gravity.TOP);
                row.setOrientation(LinearLayout.HORIZONTAL);
                widgetGroup.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            addCard(row, widgets.get(i));
        }
    }

    private void addCard(LinearLayout row, AppWidgetProviderInfo info) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(accentColor, 0x24)),
                null, roundRect(Color.WHITE, dp(8), 0x00000000)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> select(info));

        ImageView preview = new ImageView(getContext());
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        requestDrawable(preview, () -> {
            Drawable drawable = info.loadPreviewImage(getContext(), getResources().getDisplayMetrics().densityDpi);
            return drawable != null ? drawable
                    : info.loadIcon(getContext(), getResources().getDisplayMetrics().densityDpi);
        });
        card.addView(preview, new LinearLayout.LayoutParams(-1, dp(widgetPreviewHeight(info))));

        TextView name = text(widgetName(info), 14, primaryTextColor, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(4);
        card.addView(name, nameLp);
        TextView size = text(widgetSpanLabel(info), 14, secondaryTextColor, false);
        size.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(-1, -2);
        sizeLp.topMargin = dp(2);
        card.addView(size, sizeLp);
        String description = widgetDescription(info);
        if (!description.isEmpty()) {
            TextView desc = text(description, 12, secondaryTextColor, false);
            desc.setGravity(Gravity.CENTER);
            desc.setMaxLines(3);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
            descLp.topMargin = dp(2);
            card.addView(desc, descLp);
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
        cardLp.leftMargin = dp(4);
        cardLp.rightMargin = dp(4);
        row.addView(card, cardLp);
    }

    private void requestDrawable(ImageView view, DrawableLoader loader) {
        LazyDrawable request = new LazyDrawable(view, renderGeneration, loader);
        view.setTag(request);
        lazyDrawables.add(request);
    }

    private void scheduleVisibleDrawableLoad() {
        if (visibleDrawableLoadScheduled) return;
        visibleDrawableLoadScheduled = true;
        scroll.postOnAnimation(() -> {
            visibleDrawableLoadScheduled = false;
            loadVisibleDrawables();
        });
    }

    /** Load previews for visible cards and one viewport ahead, leaving the rest untouched. */
    private void loadVisibleDrawables() {
        if (getVisibility() != VISIBLE || scroll.getHeight() == 0) return;
        int[] scrollLocation = new int[2];
        scroll.getLocationOnScreen(scrollLocation);
        int viewportTop = scrollLocation[1];
        int viewportBottom = viewportTop + scroll.getHeight();
        int prefetchDistance = scroll.getHeight();

        for (LazyDrawable request : new ArrayList<>(lazyDrawables)) {
            ImageView view = request.view;
            if (request.started || request.generation != renderGeneration || !view.isShown()) continue;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int imageTop = location[1];
            int imageBottom = imageTop + Math.max(view.getHeight(), dp(40));
            if (imageBottom < viewportTop - prefetchDistance
                    || imageTop > viewportBottom + prefetchDistance) continue;

            request.started = true;
            PREVIEW_LOADER.execute(() -> {
                Drawable drawable = null;
                try { drawable = request.loader.load(); }
                catch (Exception ignored) { }
                final Drawable loaded = drawable;
                post(() -> {
                    if (loaded != null && request.generation == renderGeneration
                            && request.view.getTag() == request && request.view.isAttachedToWindow()) {
                        request.view.setImageDrawable(loaded);
                    }
                });
            });
        }
    }

    private void select(AppWidgetProviderInfo info) {
        Object service = getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        dismiss(() -> listener.onWidgetSelected(info.provider));
    }

    void dismiss() {
        dismiss(null);
    }

    private void dismiss(Runnable completion) {
        if (getVisibility() != VISIBLE || dismissing) return;
        dismissing = true;
        openAnimationGeneration++;
        afterDismiss = completion;
        animate().cancel();
        if (panelOpenAnimator != null) panelOpenAnimator.cancel();
        frostedPanel.animate().cancel();
        if (blurProgressAnimator != null) blurProgressAnimator.cancel();
        blurProgressAnimator = createBlurProgressAnimator(pickerBlurProgress, 0f, 267);
        blurProgressAnimator.start();
        animate()
                .alpha(0f)
                .setDuration(267)
                .setInterpolator(Interpolators.LINEAR)
                .withEndAction(this::finishDismiss)
                .start();
        frostedPanel.animate()
                .translationY(frostedPanel.getHeight())
                .setDuration(267)
                .setInterpolator(Interpolators.ACCELERATE)
                .start();
    }

    private void finishDismiss() {
        if (!dismissing) return;
        dismissing = false;
        if (blurProgressAnimator != null) {
            blurProgressAnimator.cancel();
            blurProgressAnimator = null;
        }
        listener.onBlurProgress(0f);
        setVisibility(GONE);
        Runnable completion = afterDismiss;
        afterDismiss = null;
        if (completion != null) {
            completion.run();
        } else {
            listener.onDismissed();
        }
    }

    void showAnimated() {
        dismissing = false;
        afterDismiss = null;
        animate().cancel();
        if (panelOpenAnimator != null) panelOpenAnimator.cancel();
        frostedPanel.animate().cancel();
        if (blurProgressAnimator != null) blurProgressAnimator.cancel();
        refreshProviders();
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate()
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(Interpolators.STANDARD)
                .start();
        int generation = ++openAnimationGeneration;
        frostedPanel.post(() -> {
            if (generation != openAnimationGeneration || dismissing
                    || getVisibility() != VISIBLE) return;
            int height = frostedPanel.getHeight();
            ObjectAnimator translation = ObjectAnimator.ofFloat(
                    frostedPanel, View.TRANSLATION_Y, height, 0f);
            translation.setDuration(267);
            translation.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
            ObjectAnimator contentFade = ObjectAnimator.ofFloat(
                    frostedPanel, View.ALPHA, 0f, 1f);
            contentFade.setDuration(150);
            contentFade.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
            AnimatorSet opening = new AnimatorSet();
            panelOpenAnimator = opening;
            blurProgressAnimator = createBlurProgressAnimator(0f, 1f, 267);
            opening.playTogether(translation, contentFade, blurProgressAnimator);
            opening.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (panelOpenAnimator == opening) panelOpenAnimator = null;
                }
                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    if (panelOpenAnimator == opening) panelOpenAnimator = null;
                }
            });
            opening.start();
        });
    }

    private ValueAnimator createBlurProgressAnimator(float from, float to, long duration) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(duration);
        animator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        animator.addUpdateListener(value -> {
            pickerBlurProgress = (Float) value.getAnimatedValue();
            listener.onBlurProgress(pickerBlurProgress);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (blurProgressAnimator == animator) blurProgressAnimator = null;
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                if (blurProgressAnimator == animator) blurProgressAnimator = null;
            }
        });
        return animator;
    }

    private void refreshProviders() {
        int oldScrollY = scroll.getScrollY();
        int requestGeneration = ++providerRefreshGeneration;
        PROVIDER_LOADER.execute(() -> {
            ProviderSnapshot snapshot = loadProviderSnapshot();
            post(() -> {
                if (requestGeneration != providerRefreshGeneration) return;
                if (snapshot == null) {
                    if (providers.isEmpty()) {
                        providerDataLoaded = true;
                        empty.setText("No widgets found");
                        render(search.getText().toString());
                    }
                    return;
                }
                providers.clear();
                providers.addAll(snapshot.providers);
                providerDataLoaded = true;
                appNames.clear();
                appNames.putAll(snapshot.appNames);
                widgetNames.clear();
                widgetNames.putAll(snapshot.widgetNames);
                widgetDescriptions.clear();
                widgetDescriptions.putAll(snapshot.widgetDescriptions);
                widgetSpanCache.clear();
                widgetSpanCache.putAll(snapshot.widgetSpans);
                rows.animate().cancel();
                rows.setAlpha(1f);
                rows.setTranslationY(0f);
                render(search.getText().toString());
                scroll.post(() -> scroll.scrollTo(0, oldScrollY));
            });
        });
    }

    void onCrossWindowBlurChanged(boolean enabled) {
        // Picker surface is intentionally opaque and does not depend on window blur.
    }

    private String appName(AppWidgetProviderInfo info) {
        String packageName = info.provider.getPackageName();
        String cached = appNames.get(packageName);
        if (cached != null) return cached;
        String label;
        try {
            label = String.valueOf(getContext().getPackageManager().getApplicationLabel(
                    getContext().getPackageManager().getApplicationInfo(packageName, 0)));
        } catch (Exception ignored) { label = packageName; }
        appNames.put(packageName, label);
        return label;
    }

    private String appNameForPackage(List<AppWidgetProviderInfo> items, String packageName) {
        for (AppWidgetProviderInfo item : items) {
            if (packageName.equals(item.provider.getPackageName())) return appName(item);
        }
        return packageName;
    }

    private String widgetName(AppWidgetProviderInfo info) {
        String cached = widgetNames.get(info.provider);
        if (cached != null) return cached;
        String label;
        try {
            CharSequence widgetLabel = info.loadLabel(getContext().getPackageManager());
            if (widgetLabel != null && widgetLabel.length() > 0) {
                String value = widgetLabel.toString();
                widgetNames.put(info.provider, value);
                return value;
            }
        } catch (RuntimeException ignored) { }
        label = info.provider.getClassName();
        widgetNames.put(info.provider, label);
        return label;
    }

    private String widgetSpanLabel(AppWidgetProviderInfo info) {
        int[] spans = widgetSpans(info);
        int spanX = spans[0];
        int spanY = spans[1];
        return spanX + " × " + spanY;
    }

    private int widgetPreviewHeight(AppWidgetProviderInfo info) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int contentWidthDp = Math.max(160, screenWidthDp - 40 - 2 * 11);
        int cellWidthDp = Math.max(52, (contentWidthDp - 6 * 8) / 3);
        int[] spans = widgetSpans(info);
        float aspect = spans[0] > 0 && spans[1] > 0
                ? spans[1] / (float) spans[0] : 1f;
        return Math.max(64, Math.min(176, Math.round(cellWidthDp * aspect)));
    }

    /** Estimate launcher spans from the provider's declared dp size and this device's grid. */
    private int[] widgetSpans(AppWidgetProviderInfo info) {
        float density = getResources().getDisplayMetrics().density;
        int[] cached = widgetSpanCache.get(info.provider);
        if (cached != null) return cached;
        int[] spans = calculateWidgetSpans(info, density,
                getResources().getDisplayMetrics().widthPixels);
        widgetSpanCache.put(info.provider, spans);
        return spans;
    }

    private static int[] calculateWidgetSpans(AppWidgetProviderInfo info, float density,
                                               int widthPixels) {
        // AppWidgetProviderInfo min sizes are already expressed in dp.
        float widthDp = info.minWidth;
        float heightDp = info.minHeight;
        int screenWidthDp = Math.round(widthPixels / density);
        // Launcher grid width is device-specific. Use the common five-column profile as a
        // display estimate, rather than pretending every grid cell is a fixed 72dp.
        float cellWidthDp = Math.max(48f, (screenWidthDp - 32f) / 5f);
        float cellHeightDp = 64f;
        int spanX = Math.max(1, (int) Math.ceil(widthDp / cellWidthDp));
        int spanY = Math.max(1, (int) Math.ceil(heightDp / cellHeightDp));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                java.lang.reflect.Field x = AppWidgetProviderInfo.class.getField("targetCellWidth");
                java.lang.reflect.Field y = AppWidgetProviderInfo.class.getField("targetCellHeight");
                int targetX = x.getInt(info);
                int targetY = y.getInt(info);
                if (targetX > 0) spanX = targetX;
                if (targetY > 0) spanY = targetY;
            } catch (ReflectiveOperationException ignored) { }
        }
        return new int[]{spanX, spanY};
    }

    private String widgetDescription(AppWidgetProviderInfo info) {
        if (widgetDescriptions.containsKey(info.provider)) {
            return widgetDescriptions.get(info.provider);
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return "";
        try {
            CharSequence description = info.loadDescription(getContext());
            String value = description == null ? "" : description.toString().trim();
            widgetDescriptions.put(info.provider, value);
            return value;
        } catch (RuntimeException ignored) {
            widgetDescriptions.put(info.provider, "");
            return "";
        }
    }

    private RippleDrawable headerRipple(boolean firstSection, boolean lastSection, boolean expanded) {
        GradientDrawable fill = new GradientDrawable();
        // The shared rows card supplies the surface. Keep headers transparent
        // so the first app does not draw a second rounded card over it.
        fill.setColor(Color.TRANSPARENT);
        int top = dp(firstSection ? 28 : 4);
        int bottom = expanded ? 0 : dp(lastSection ? 28 : 4);
        fill.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(accentColor, 0x24)), fill, mask);
    }

    private Drawable appSectionSurface(boolean firstSection, boolean lastSection) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(glassColor);
        float top = dp(firstSection ? 28 : 4);
        float bottom = dp(lastSection ? 28 : 4);
        drawable.setCornerRadii(new float[]{top, top, top, top,
                bottom, bottom, bottom, bottom});
        return drawable;
    }

    private void applyHeaderSurface(View header, boolean firstSection, boolean lastSection,
                                    boolean expanded) {
        header.setBackground(headerRipple(firstSection, lastSection, expanded));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    /** Motorola leaves the outer sheet transparent and rounds only its top edge. */
    private Drawable frostedSurface() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        float top = dp(30);
        drawable.setCornerRadii(new float[]{top, top, top, top, 0, 0, 0, 0});
        drawable.setStroke(dp(1), Color.TRANSPARENT);
        return drawable;
    }

    private int systemColor(Context context, String name, int fallback) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return fallback;
        int id = context.getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try { return context.getColor(id); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
