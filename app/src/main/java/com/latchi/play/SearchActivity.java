package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified search across TMDB + all catalog providers, merged and deduplicated.
 * The user never sees which source provided a result.
 */
public class SearchActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private boolean television;
    private TmdbClient tmdb;
    private AppPrefs prefs;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ProgressBar progress;
    private ContentStateView stateView;
    private TextView screenTitle;
    private EditText input;
    private int requestGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        tmdb = new TmdbClient(this);
        prefs = new AppPrefs(this);
        buildUi();
        if (television) input.requestFocus();
        else input.postDelayed(() -> {
            if (input != null) input.requestFocus();
        }, 250);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(television ? 28 : 14), dp(10), dp(television ? 28 : 10), dp(10));
        header.setBackgroundColor(Color.rgb(18, 15, 25));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(television ? 78 : 64)));

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(television ? 15 : 13);
        back.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        back.setBackground(pill(PURPLE, dp(14), GOLD));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(television ? 110 : 80), dp(television ? 48 : 40)));

        screenTitle = text(getString(R.string.search), television ? 22 : 17, GOLD, true);
        screenTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        screenTitle.setPadding(dp(12), 0, dp(12), 0);
        header.addView(screenTitle, new LinearLayout.LayoutParams(0, -1, 1));

        input = new EditText(this);
        input.setHint(R.string.search_hint);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(120, 114, 134));
        input.setTextSize(television ? 17 : 15);
        input.setBackground(pill(Color.rgb(13, 10, 18), dp(12), Color.rgb(75, 58, 96)));
        input.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -1, 1);
        inputParams.setMargins(dp(4), 0, dp(4), 0);
        header.addView(input, inputParams);

        Button go = new Button(this);
        go.setText(R.string.search);
        go.setAllCaps(false);
        go.setTextColor(Color.WHITE);
        go.setTextSize(television ? 15 : 13);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setBackground(pill(PURPLE, dp(14), GOLD));
        go.setOnClickListener(v -> runSearch());
        header.addView(go, new LinearLayout.LayoutParams(dp(television ? 110 : 76), dp(television ? 48 : 40)));

        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(television ? 16 : 5), dp(television ? 24 : 4), dp(24));
        grid.setItemAnimator(null);
        grid.setDescendantFocusability(View.FOCUS_AFTER_DESCENDANTS);
        grid.setLayoutManager(new GridLayoutManager(this, DeviceUtils.catalogColumns(this, television)));
        adapter = new PosterAdapter(television, this::openDetails);
        grid.setAdapter(adapter);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        stateView.showMessage(getString(R.string.search_prompt));
    }

    private void runSearch() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) {
            input.setError(getString(R.string.search_hint));
            return;
        }
        final int generation = ++requestGeneration;
        progress.setVisibility(View.VISIBLE);
        stateView.showMessage(getString(R.string.loading_content));
        grid.setVisibility(View.GONE);

        final List<CatalogItem> results = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(3);

        Runnable collect = () -> {
            if (pending.decrementAndGet() > 0) return;
            runOnUiThread(() -> {
                if (!isCurrentRequest(generation)) return;
                progress.setVisibility(View.GONE);
                List<CatalogItem> merged = mergeAndDedup(results);
                if (merged.isEmpty()) {
                    grid.setVisibility(View.GONE);
                    stateView.showAction(getString(R.string.no_results),
                            getString(R.string.retry), v -> runSearch());
                    return;
                }
                stateView.hide();
                grid.setVisibility(View.VISIBLE);
                adapter.submit(merged);
            });
        };

        tmdb.search(query, 1, new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                results.addAll(items);
                collect.run();
            }

            @Override
            public void onError() {
                collect.run();
            }
        });

        for (ContentProvider provider : ProviderRegistry.catalogProviders(this)) {
            provider.search(query, 1, new ContentProvider.CatalogCallback() {
                @Override
                public void onSuccess(List<CatalogItem> items, boolean hasMore) {
                    results.addAll(items);
                    collect.run();
                }

                @Override
                public void onError() {
                    collect.run();
                }
            });
        }
    }

    private List<CatalogItem> mergeAndDedup(List<CatalogItem> items) {
        Map<String, CatalogItem> unique = new LinkedHashMap<>();
        for (CatalogItem item : items) {
            String key = dedupKey(item);
            CatalogItem existing = unique.get(key);
            if (existing == null) {
                unique.put(key, item);
            } else if (!existing.providerId.equals(item.providerId)) {
                // Same content from another provider: keep both as playback alternatives
                // by keeping the richer item (with tmdb id when available).
                if (existing.tmdbId <= 0 && item.tmdbId > 0) {
                    unique.put(key, item);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String dedupKey(CatalogItem item) {
        String title = normalize(item.title);
        String year = item.year == null ? "" : item.year.trim();
        return title + "|" + year;
    }

    private String normalize(String value) {
        String n = value == null ? "" : value.toLowerCase(Locale.US).trim();
        n = n.replaceAll("[\\u064B-\\u0652\\u0670\\u0640]", "");
        n = n.replaceAll("[^a-z0-9\\u0600-\\u06FF ]", " ");
        return n.replaceAll("\\s+", " ").trim();
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && !isFinishing() && !isDestroyed();
    }

    private void openDetails(CatalogItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable pill(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        requestGeneration++;
        if (tmdb != null) tmdb.destroy();
        super.onDestroy();
    }
}
