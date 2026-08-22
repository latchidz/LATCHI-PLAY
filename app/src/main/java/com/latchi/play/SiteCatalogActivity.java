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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Browses a web-site provider's public catalog (home / movies / series / search)
 * inside the same native poster grid. Playback always goes through the provider
 * registry and the native player.
 */
public class SiteCatalogActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private enum Feed { HOME, MOVIES, SERIES, SEARCH }

    private boolean television;
    private ContentProvider provider;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ProgressBar progress;
    private ProgressBar paginationProgress;
    private Button paginationRetry;
    private TextView screenTitle;
    private ContentStateView stateView;

    private final List<CatalogItem> currentItems = new ArrayList<>();
    private Feed feed = Feed.HOME;
    private String query = "";
    private int page;
    private boolean hasMore;
    private boolean loading;
    private boolean loadingNext;
    private int requestGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        String providerId = getIntent().getStringExtra("provider");
        provider = ProviderRegistry.byId(this, providerId);
        if (provider == null || !provider.supportsCatalog()) {
            finish();
            return;
        }
        buildUi();
        loadFeed(Feed.HOME);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(television ? 28 : 14), dp(7), dp(television ? 28 : 10), dp(7));
        header.setBackgroundColor(SURFACE);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(television ? 72 : 58)));

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(television ? 15 : 13);
        back.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        back.setBackground(pill(PURPLE, dp(14), GOLD));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(television ? 110 : 80), dp(television ? 48 : 40)));

        screenTitle = text("", television ? 22 : 17, GOLD, true);
        screenTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        screenTitle.setPadding(dp(12), 0, dp(12), 0);
        header.addView(screenTitle, new LinearLayout.LayoutParams(0, -1, 1));

        Button search = new Button(this);
        search.setText(R.string.search);
        search.setAllCaps(false);
        search.setTextColor(Color.WHITE);
        search.setTextSize(television ? 15 : 13);
        search.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        search.setBackground(pill(PURPLE, dp(14), GOLD));
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(television ? 110 : 76), dp(television ? 48 : 40)));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(television ? 22 : 7), dp(6), dp(television ? 22 : 7), dp(6));
        nav.setBackgroundColor(Color.rgb(12, 10, 17));
        if (television) {
            root.addView(nav, new LinearLayout.LayoutParams(-1, dp(66)));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(nav, new HorizontalScrollView.LayoutParams(-2, -1));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(56)));
        }
        addNav(nav, R.string.home, v -> loadFeed(Feed.HOME));
        addNav(nav, R.string.movies, v -> loadFeed(Feed.MOVIES));
        addNav(nav, R.string.series, v -> loadFeed(Feed.SERIES));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(television ? 16 : 5), dp(television ? 24 : 4), dp(82));
        grid.setItemAnimator(null);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.setLayoutManager(new GridLayoutManager(this, DeviceUtils.catalogColumns(this, television)));
        adapter = new PosterAdapter(television, this::openDetails);
        grid.setAdapter(adapter);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));

        paginationProgress = new ProgressBar(this);
        paginationProgress.setIndeterminate(true);
        paginationProgress.setContentDescription(getString(R.string.loading_more));
        paginationProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        paginationProgress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(television ? 48 : 40), dp(television ? 48 : 40),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        progressParams.bottomMargin = dp(12);
        content.addView(paginationProgress, progressParams);

        paginationRetry = new Button(this);
        paginationRetry.setText(R.string.load_more);
        paginationRetry.setAllCaps(false);
        paginationRetry.setTextColor(Color.WHITE);
        paginationRetry.setTextSize(television ? 15 : 13);
        paginationRetry.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paginationRetry.setBackground(pill(PURPLE, dp(14), GOLD));
        paginationRetry.setVisibility(View.GONE);
        paginationRetry.setOnClickListener(view -> loadMore());
        FrameLayout.LayoutParams retryParams = new FrameLayout.LayoutParams(
                dp(television ? 230 : 180), dp(television ? 54 : 46),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        retryParams.bottomMargin = dp(12);
        content.addView(paginationRetry, retryParams);

        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || loadingNext || !hasMore) return;
                GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (manager == null) return;
                int threshold = television ? 10 : 6;
                if (manager.findLastVisibleItemPosition() >= adapter.getItemCount() - threshold) {
                    loadMore();
                }
            }
        });
    }

    private void loadFeed(Feed target) {
        loadFeed(target, "");
    }

    private void loadFeed(Feed target, String searchQuery) {
        if (loading) return;
        feed = target;
        query = searchQuery;
        page = 0;
        hasMore = false;
        loading = true;
        loadingNext = false;
        currentItems.clear();
        int generation = ++requestGeneration;

        progress.setVisibility(View.VISIBLE);
        paginationProgress.setVisibility(View.GONE);
        paginationRetry.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        stateView.showMessage(getString(R.string.loading_content));

        switch (target) {
            case HOME:
                screenTitle.setText(provider.label(this));
                break;
            case MOVIES:
                screenTitle.setText(getString(R.string.movies));
                break;
            case SERIES:
                screenTitle.setText(getString(R.string.series));
                break;
            case SEARCH:
                screenTitle.setText(getString(R.string.search_results, searchQuery));
                break;
        }

        ContentProvider.CatalogCallback callback = new ContentProvider.CatalogCallback() {
            @Override
            public void onSuccess(List<CatalogItem> items, boolean more) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    page = 1;
                    hasMore = more;
                    if (items.isEmpty()) {
                        stateView.showMessage(getString(R.string.no_results));
                        return;
                    }
                    currentItems.addAll(items);
                    stateView.hide();
                    grid.setVisibility(View.VISIBLE);
                    adapter.submit(currentItems);
                    updatePaginationControl();
                    if (television) {
                        grid.postDelayed(() -> {
                            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
                            if (holder != null) holder.itemView.requestFocus();
                            else grid.requestFocus();
                        }, 250);
                    }
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    paginationProgress.setVisibility(View.GONE);
                    paginationRetry.setVisibility(View.GONE);
                    grid.setVisibility(View.GONE);
                    stateView.showAction(getString(R.string.load_content_failed),
                            getString(R.string.retry), v -> loadFeed(feed, query));
                });
            }
        };

        switch (target) {
            case HOME:
                provider.home(1, callback);
                break;
            case MOVIES:
                provider.movies(1, callback);
                break;
            case SERIES:
                provider.series(1, callback);
                break;
            case SEARCH:
            default:
                provider.search(searchQuery, 1, callback);
                break;
        }
    }

    private void loadMore() {
        if (loading || loadingNext || !hasMore) return;
        final int nextPage = page + 1;
        final int generation = requestGeneration;
        loadingNext = true;
        paginationRetry.setVisibility(View.GONE);
        paginationProgress.setVisibility(View.VISIBLE);

        ContentProvider.CatalogCallback callback = new ContentProvider.CatalogCallback() {
            @Override
            public void onSuccess(List<CatalogItem> items, boolean more) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loadingNext = false;
                    paginationProgress.setVisibility(View.GONE);
                    mergeItems(items);
                    page = nextPage;
                    hasMore = more;
                    adapter.submit(currentItems);
                    updatePaginationControl();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loadingNext = false;
                    paginationProgress.setVisibility(View.GONE);
                    paginationRetry.setText(R.string.retry_more);
                    paginationRetry.setVisibility(View.VISIBLE);
                });
            }
        };

        switch (feed) {
            case MOVIES:
                provider.movies(nextPage, callback);
                break;
            case SERIES:
                provider.series(nextPage, callback);
                break;
            case SEARCH:
                provider.search(query, nextPage, callback);
                break;
            case HOME:
            default:
                provider.home(nextPage, callback);
                break;
        }
    }

    private void updatePaginationControl() {
        paginationProgress.setVisibility(View.GONE);
        if (!hasMore) {
            paginationRetry.setVisibility(View.GONE);
        } else {
            paginationRetry.setText(R.string.load_more);
            paginationRetry.setVisibility(View.VISIBLE);
        }
    }

    private void mergeItems(List<CatalogItem> incoming) {
        Map<String, CatalogItem> unique = new LinkedHashMap<>();
        for (CatalogItem item : currentItems) unique.put(item.pageUrl, item);
        for (CatalogItem item : incoming) unique.put(item.pageUrl, item);
        currentItems.clear();
        currentItems.addAll(unique.values());
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && !isFinishing() && !isDestroyed();
    }

    private void openDetails(CatalogItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint(R.string.search_hint);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.search)
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.search, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                loadFeed(Feed.SEARCH, text);
                dialog.dismiss();
            }
        }));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
        input.requestFocus();
        if (!television) dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void addNav(LinearLayout nav, int stringRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(stringRes);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(television ? 16 : 13);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setFocusable(television);
        button.setFocusableInTouchMode(television);
        button.setBackground(pill(Color.rgb(29, 24, 40), dp(14), Color.rgb(75, 58, 96)));
        if (television) {
            button.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(pill(focused ? PURPLE : Color.rgb(29, 24, 40), dp(14),
                        focused ? GOLD : Color.rgb(75, 58, 96)));
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(120).start();
            });
        }
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = television
                ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(dp(96), -1);
        params.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(button, params);
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
}
