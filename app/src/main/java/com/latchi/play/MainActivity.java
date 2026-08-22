package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
 * Home screen backed by the TMDB API: trending, popular movies, popular series,
 * search and genre discovery. Playback sources are handled separately by providers.
 */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private enum Feed {
        HOME, MOVIES, SERIES, SEARCH, GENRE
    }

    private boolean television;
    private boolean configuredBefore;
    private TmdbClient tmdb;
    private AppPrefs prefs;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ProgressBar progress;
    private ProgressBar paginationProgress;
    private Button paginationRetry;
    private TextView screenTitle;
    private ContentStateView stateView;
    private UpdateManager updateManager;

    private final List<CatalogItem> currentItems = new ArrayList<>();
    private Feed feed = Feed.HOME;
    private String query = "";
    private int genreId;
    private String genreName = "";
    private String genreMediaType = "movie";
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tmdb = new TmdbClient(this);
        prefs = new AppPrefs(this);
        configuredBefore = prefs.hasTmdbKey();
        buildUi();
        updateManager = new UpdateManager(this);
        updateManager.checkAutomatically();
        if (prefs.hasTmdbKey()) {
            loadFeed(Feed.HOME);
        } else {
            showKeyMissing();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
        boolean nowConfigured = prefs.hasTmdbKey();
        if (!configuredBefore && nowConfigured) {
            configuredBefore = true;
            loadFeed(Feed.HOME);
        }
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

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setContentDescription(getString(R.string.app_name));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(icon, new LinearLayout.LayoutParams(dp(television ? 52 : 40), dp(television ? 52 : 40)));

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(10), 0, dp(10), 0);
        header.addView(brandBox, new LinearLayout.LayoutParams(0, -1, 1));
        TextView brand = text(getString(R.string.app_name), television ? 24 : 18, GOLD, true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        brandBox.addView(brand, new LinearLayout.LayoutParams(-1, 0, 1));
        screenTitle = text("", television ? 13 : 11, Color.rgb(175, 167, 190), false);
        screenTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        brandBox.addView(screenTitle, new LinearLayout.LayoutParams(-1, 0, 1));

        Button search = actionButton(getString(R.string.search));
        search.setContentDescription(getString(R.string.search));
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(television ? 110 : 72), dp(television ? 48 : 40)));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(television ? 22 : 7), dp(6), dp(television ? 22 : 7), dp(6));
        nav.setBackgroundColor(Color.rgb(12, 10, 17));
        if (television) {
            root.addView(nav, new LinearLayout.LayoutParams(-1, dp(68)));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setFillViewport(false);
            scroll.setBackgroundColor(Color.rgb(12, 10, 17));
            scroll.addView(nav, new HorizontalScrollView.LayoutParams(-2, -1));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(58)));
        }
        addNav(nav, getString(R.string.home), v -> loadFeed(Feed.HOME));
        addNav(nav, getString(R.string.movies), v -> showGenreDialog(true));
        addNav(nav, getString(R.string.series), v -> showGenreDialog(false));
        addNav(nav, getString(R.string.favorites), v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        addNav(nav, getString(R.string.history), v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        addNav(nav, getString(R.string.settings), v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        addNav(nav, getString(R.string.update), v -> updateManager.checkManually());

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
        grid.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        int columns = DeviceUtils.catalogColumns(this, television);
        grid.setLayoutManager(new GridLayoutManager(this, columns));
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

        paginationRetry = actionButton(getString(R.string.load_more));
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

    private void showKeyMissing() {
        screenTitle.setText(getString(R.string.app_name));
        progress.setVisibility(View.GONE);
        paginationProgress.setVisibility(View.GONE);
        paginationRetry.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        stateView.showAction(getString(R.string.tmdb_key_missing),
                getString(R.string.open_settings),
                v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void loadFeed(Feed target) {
        loadFeed(target, "", 0);
    }

    private void loadFeed(Feed target, String searchQuery, int genre) {
        if (loading) return;

        feed = target;
        query = searchQuery;
        genreId = genre;
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

        if (!prefs.hasTmdbKey()) {
            loading = false;
            progress.setVisibility(View.GONE);
            showKeyMissing();
            return;
        }

        if (target == Feed.MOVIES || target == Feed.SERIES) {
            screenTitle.setText(target == Feed.MOVIES
                    ? getString(R.string.popular_movies) : getString(R.string.popular_series));
        } else if (target == Feed.HOME) {
            screenTitle.setText(getString(R.string.trending_week));
        } else if (target == Feed.GENRE) {
            screenTitle.setText(genreName.isEmpty() ? getString(R.string.movies) : genreName);
        } else {
            screenTitle.setText(getString(R.string.search_results, searchQuery));
        }

        TmdbClient.Callback<List<CatalogItem>> callback = new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    page = 1;
                    hasMore = items.size() >= 20;
                    if (items.isEmpty()) {
                        stateView.showMessage(getString(R.string.no_results));
                        return;
                    }
                    currentItems.addAll(items);
                    stateView.hide();
                    grid.setVisibility(View.VISIBLE);
                    adapter.submit(currentItems);
                    updatePaginationControl();
                    focusFirstCard();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    showLoadError();
                });
            }
        };

        switch (target) {
            case HOME:
                tmdb.trending(1, callback);
                break;
            case MOVIES:
                tmdb.popular("movie", 1, callback);
                break;
            case SERIES:
                tmdb.popular("tv", 1, callback);
                break;
            case SEARCH:
                tmdb.search(searchQuery, 1, callback);
                break;
            case GENRE:
            default:
                tmdb.discover(genreMediaType, genre, 1, callback);
                break;
        }
    }

    private void loadMore() {
        if (loading || loadingNext || !hasMore) return;
        if (!DeviceUtils.hasInternetConnection(this)) {
            screenTitle.setText(getString(R.string.offline_title, screenTitle.getText()));
            paginationRetry.setText(R.string.retry_more);
            paginationRetry.setVisibility(View.VISIBLE);
            return;
        }

        final int nextPage = page + 1;
        final int generation = requestGeneration;
        loadingNext = true;
        paginationRetry.setVisibility(View.GONE);
        paginationProgress.setVisibility(View.VISIBLE);

        TmdbClient.Callback<List<CatalogItem>> callback = new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loadingNext = false;
                    paginationProgress.setVisibility(View.GONE);
                    mergeItems(items);
                    page = nextPage;
                    hasMore = items.size() >= 20;
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
            case HOME:
                tmdb.trending(nextPage, callback);
                break;
            case MOVIES:
                tmdb.popular("movie", nextPage, callback);
                break;
            case SERIES:
                tmdb.popular("tv", nextPage, callback);
                break;
            case SEARCH:
                tmdb.search(query, nextPage, callback);
                break;
            case GENRE:
            default:
                tmdb.discover(genreMediaType, genreId, nextPage, callback);
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

    private void showLoadError() {
        paginationProgress.setVisibility(View.GONE);
        paginationRetry.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        stateView.showAction(getString(R.string.load_content_failed),
                getString(R.string.retry),
                v -> loadFeed(feed, query, genreId));
    }

    private void focusFirstCard() {
        if (!television) return;
        grid.postDelayed(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
            else grid.requestFocus();
        }, 250);
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && !isFinishing() && !isDestroyed();
    }

    private void openDetails(CatalogItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private void showGenreDialog(boolean movies) {
        if (!DeviceUtils.hasInternetConnection(this)) {
            screenTitle.setText(getString(R.string.offline_title, screenTitle.getText()));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        tmdb.genreList(movies ? "movie" : "tv", new TmdbClient.Callback<List<TmdbClient.Genre>>() {
            @Override
            public void onSuccess(List<TmdbClient.Genre> genres) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (isFinishing() || isDestroyed() || genres.isEmpty()) return;
                    String allLabel = movies ? getString(R.string.all_movies) : getString(R.string.all_series);
                    String[] labels = new String[genres.size() + 1];
                    labels[0] = allLabel;
                    for (int i = 0; i < genres.size(); i++) labels[i + 1] = genres.get(i).name;
                    AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(movies ? R.string.choose_genre_movies : R.string.choose_genre_series)
                            .setItems(labels, (d, which) -> {
                                if (which == 0) {
                                    loadFeed(movies ? Feed.MOVIES : Feed.SERIES);
                                    return;
                                }
                                TmdbClient.Genre genre = genres.get(which - 1);
                                genreMediaType = movies ? "movie" : "tv";
                                genreId = genre.id;
                                genreName = genre.name;
                                loadFeed(Feed.GENRE, "", genre.id);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .create();
                    dialog.setOnShowListener(v -> {
                        if (television) dialog.getListView().requestFocus();
                    });
                    dialog.show();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    ToastMessage(getString(R.string.load_content_failed));
                });
            }
        });
    }

    private void ToastMessage(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
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
                loadFeed(Feed.SEARCH, text, 0);
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
        if (!television) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void addNav(LinearLayout nav, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
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

    private Button actionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(television ? 15 : 12);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackground(pill(PURPLE, dp(14), GOLD));
        b.setFocusable(television);
        b.setFocusableInTouchMode(television);
        return b;
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
        if (updateManager != null) updateManager.destroy();
        super.onDestroy();
    }
}
