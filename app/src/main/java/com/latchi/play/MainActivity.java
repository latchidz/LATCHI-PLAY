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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

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
        HOME, MOVIES, SERIES, GENRE
    }

    private boolean television;
    private boolean configuredBefore;
    private TmdbClient tmdb;
    private AppPrefs prefs;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private LinearLayout heroBox;
    private ImageView heroBackdrop;
    private TextView heroTitle;
    private TextView heroMeta;
    private Button heroWatch;
    private Button heroDetails;
    private LinearLayout categoriesRow;
    private LinearLayout continueBox;
    private RecyclerView continueRecycler;
    private ContinueWatchingAdapter continueAdapter;
    private LinearLayout latestMoviesBox;
    private LinearLayout latestSeriesBox;
    private RecyclerView latestMoviesRecycler;
    private RecyclerView latestSeriesRecycler;
    private RowPosterAdapter latestMoviesAdapter;
    private RowPosterAdapter latestSeriesAdapter;
    private HistoryStore historyStore;
    private ProgressBar progress;
    private ProgressBar paginationProgress;
    private Button paginationRetry;
    private TextView screenTitle;
    private ContentStateView stateView;
    private UpdateManager updateManager;
    private CatalogItem heroItem;

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
        historyStore = new HistoryStore(this);
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
        refreshContinueRow();
    }

    private void refreshContinueRow() {
        if (continueBox == null || continueAdapter == null || historyStore == null) return;
        boolean showRow = feed == Feed.HOME;
        if (!showRow) {
            continueBox.setVisibility(View.GONE);
            return;
        }
        List<HistoryEntry> all = historyStore.getAll();
        List<HistoryEntry> inProgress = new ArrayList<>();
        for (HistoryEntry entry : all) {
            if (entry.positionMs >= 5_000 && entry.progressPercent() < 98) {
                inProgress.add(entry);
                if (inProgress.size() >= 14) break;
            }
        }
        if (inProgress.isEmpty()) {
            continueBox.setVisibility(View.GONE);
            return;
        }
        continueAdapter.submit(inProgress);
        continueBox.setVisibility(View.VISIBLE);
    }

    private void openContinue(HistoryEntry entry) {
        Intent intent = new Intent(this, WatchActivity.class);
        intent.putExtra("item", entry.item);
        intent.putExtra("title", entry.item.title);
        startActivity(intent);
    }

    private void showSitesDialog() {
        List<ContentProvider> sites = ProviderRegistry.catalogProviders(this);
        if (sites.isEmpty()) {
            ToastMessage(getString(R.string.no_sites_available));
            return;
        }
        String[] labels = new String[sites.size()];
        String[] ids = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            labels[i] = sites.get(i).label(this);
            ids[i] = sites.get(i).id();
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sites)
                .setItems(labels, (d, which) -> {
                    Intent intent = new Intent(MainActivity.this, SiteCatalogActivity.class);
                    intent.putExtra("provider", ids[which]);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(v -> {
            if (television) dialog.getListView().requestFocus();
        });
        dialog.show();
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
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
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
        addNav(nav, getString(R.string.sites), v -> showSitesDialog());
        addNav(nav, getString(R.string.update), v -> updateManager.checkManually());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        content.addView(inner, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- Hero banner ----------------
        heroBox = new LinearLayout(this);
        heroBox.setOrientation(LinearLayout.VERTICAL);
        heroBox.setVisibility(View.GONE);
        inner.addView(heroBox, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout hero = new FrameLayout(this);
        heroBox.addView(hero, new LinearLayout.LayoutParams(-1, dp(television ? 380 : 250)));
        heroBackdrop = new ImageView(this);
        heroBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBackdrop.setBackgroundColor(Color.rgb(16, 13, 24));
        hero.addView(heroBackdrop, new FrameLayout.LayoutParams(-1, -1));
        View heroGradient = new View(this);
        heroGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(70, 0, 0, 0), BG}));
        hero.addView(heroGradient, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setGravity(Gravity.RIGHT);
        heroInfo.setPadding(dp(television ? 42 : 18), dp(20), dp(television ? 42 : 18), dp(14));
        hero.addView(heroInfo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView heroBadge = text(getString(R.string.trending_week), television ? 14 : 11,
                GOLD, true);
        heroInfo.addView(heroBadge, new LinearLayout.LayoutParams(-1, -2));
        heroTitle = text("", television ? 34 : 23, Color.WHITE, true);
        heroTitle.setGravity(Gravity.RIGHT);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroTitle.setPadding(0, dp(6), 0, dp(2));
        heroInfo.addView(heroTitle, new LinearLayout.LayoutParams(-1, -2));
        heroMeta = text("", television ? 16 : 12, Color.rgb(214, 208, 224), false);
        heroMeta.setGravity(Gravity.RIGHT);
        heroMeta.setPadding(0, dp(2), 0, dp(10));
        heroInfo.addView(heroMeta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout heroButtons = new LinearLayout(this);
        heroButtons.setOrientation(LinearLayout.HORIZONTAL);
        heroButtons.setGravity(Gravity.RIGHT);
        heroInfo.addView(heroButtons, new LinearLayout.LayoutParams(-1, -2));
        heroWatch = new Button(this);
        heroWatch.setText(R.string.watch_now);
        heroWatch.setAllCaps(false);
        heroWatch.setTextColor(Color.WHITE);
        heroWatch.setTextSize(television ? 17 : 14);
        heroWatch.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heroWatch.setBackground(pill(PURPLE, dp(14), GOLD));
        heroWatch.setOnClickListener(v -> {
            if (heroItem != null) openWatch(heroItem);
        });
        heroButtons.addView(heroWatch, new LinearLayout.LayoutParams(
                television ? dp(230) : dp(150), dp(television ? 58 : 46)));
        heroDetails = new Button(this);
        heroDetails.setText(R.string.details);
        heroDetails.setAllCaps(false);
        heroDetails.setTextColor(Color.WHITE);
        heroDetails.setTextSize(television ? 17 : 14);
        heroDetails.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heroDetails.setBackground(pill(Color.rgb(38, 32, 50), dp(14), Color.rgb(120, 100, 150)));
        heroDetails.setOnClickListener(v -> {
            if (heroItem != null) openDetails(heroItem);
        });
        LinearLayout.LayoutParams heroDetailsParams = new LinearLayout.LayoutParams(
                television ? dp(190) : dp(130), dp(television ? 58 : 46));
        heroDetailsParams.setMargins(dp(10), 0, 0, 0);
        heroButtons.addView(heroDetails, heroDetailsParams);

        // ---------------- Categories chips ----------------
        HorizontalScrollView categoriesScroll = new HorizontalScrollView(this);
        categoriesScroll.setHorizontalScrollBarEnabled(false);
        categoriesScroll.setVisibility(View.GONE);
        categoriesRow = new LinearLayout(this);
        categoriesRow.setOrientation(LinearLayout.HORIZONTAL);
        categoriesRow.setGravity(Gravity.RIGHT);
        categoriesRow.setPadding(dp(television ? 24 : 14), dp(8), dp(television ? 24 : 14), dp(4));
        categoriesScroll.addView(categoriesRow, new HorizontalScrollView.LayoutParams(-2, -1));
        inner.addView(categoriesScroll, new LinearLayout.LayoutParams(-1,
                dp(television ? 52 : 44)));

        continueBox = new LinearLayout(this);
        continueBox.setOrientation(LinearLayout.VERTICAL);
        continueBox.setVisibility(View.GONE);
        inner.addView(continueBox, new LinearLayout.LayoutParams(-1, -2));

        TextView continueHeading = text(getString(R.string.continue_watching),
                television ? 18 : 15, GOLD, true);
        continueHeading.setGravity(Gravity.RIGHT);
        continueHeading.setPadding(dp(television ? 24 : 14), dp(12), dp(television ? 24 : 14), dp(2));
        continueBox.addView(continueHeading, new LinearLayout.LayoutParams(-1, -2));

        continueRecycler = new RecyclerView(this);
        continueRecycler.setClipToPadding(false);
        continueRecycler.setItemAnimator(null);
        continueRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        continueAdapter = new ContinueWatchingAdapter(television, this::openContinue);
        continueRecycler.setAdapter(continueAdapter);
        continueBox.addView(continueRecycler, new LinearLayout.LayoutParams(
                -1, dp(television ? 262 : 206)));

        // ---------------- Latest movies / series rows ----------------
        latestMoviesBox = buildRowBox();
        inner.addView(latestMoviesBox, new LinearLayout.LayoutParams(-1, -2));
        latestMoviesRecycler = new RecyclerView(this);
        latestMoviesRecycler.setClipToPadding(false);
        latestMoviesRecycler.setItemAnimator(null);
        latestMoviesRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        latestMoviesAdapter = new RowPosterAdapter(television, this::openDetails);
        latestMoviesRecycler.setAdapter(latestMoviesAdapter);
        latestMoviesBox.addView(latestMoviesRecycler, new LinearLayout.LayoutParams(
                -1, dp(television ? 258 : 204)));

        latestSeriesBox = buildRowBox();
        inner.addView(latestSeriesBox, new LinearLayout.LayoutParams(-1, -2));
        latestSeriesRecycler = new RecyclerView(this);
        latestSeriesRecycler.setClipToPadding(false);
        latestSeriesRecycler.setItemAnimator(null);
        latestSeriesRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        latestSeriesAdapter = new RowPosterAdapter(television, this::openDetails);
        latestSeriesRecycler.setAdapter(latestSeriesAdapter);
        latestSeriesBox.addView(latestSeriesRecycler, new LinearLayout.LayoutParams(
                -1, dp(television ? 258 : 204)));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(television ? 16 : 5), dp(television ? 24 : 4), dp(82));
        grid.setItemAnimator(null);
        grid.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        int columns = DeviceUtils.catalogColumns(this, television);
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        adapter = new PosterAdapter(television, this::openDetails);
        grid.setAdapter(adapter);
        inner.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

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

    private LinearLayout buildRowBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        TextView heading = new TextView(this);
        heading.setTextColor(GOLD);
        heading.setTextSize(television ? 18 : 15);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setGravity(Gravity.RIGHT);
        heading.setPadding(dp(television ? 24 : 14), dp(12), dp(television ? 24 : 14), dp(2));
        box.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private void setLatestHeading(LinearLayout box, String label) {
        if (box != null && box.getChildCount() > 0 && box.getChildAt(0) instanceof TextView) {
            ((TextView) box.getChildAt(0)).setText(label);
        }
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
        setHomeDecorVisible(target == Feed.HOME);

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
                    refreshContinueRow();
                    if (target == Feed.HOME) {
                        updateHero(items);
                        loadHomeExtras();
                    }
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
            case GENRE:
            default:
                tmdb.discover(genreMediaType, genre, 1, callback);
                break;
        }
    }

    private void setHomeDecorVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (heroBox != null) heroBox.setVisibility(visible && heroItem != null
                ? View.VISIBLE : View.GONE);
        if (categoriesRow != null && categoriesRow.getParent() instanceof View) {
            ((View) categoriesRow.getParent()).setVisibility(visible
                    && categoriesRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }
        if (latestMoviesBox != null) {
            latestMoviesBox.setVisibility(visible && latestMoviesAdapter != null
                    && latestMoviesAdapter.size() > 0 ? View.VISIBLE : View.GONE);
        }
        if (latestSeriesBox != null) {
            latestSeriesBox.setVisibility(visible && latestSeriesAdapter != null
                    && latestSeriesAdapter.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateHero(List<CatalogItem> items) {
        if (items == null || items.isEmpty()) {
            heroBox.setVisibility(View.GONE);
            heroItem = null;
            return;
        }
        heroItem = items.get(0);
        heroTitle.setText(heroItem.title);
        StringBuilder meta = new StringBuilder();
        if (!heroItem.year.isEmpty()) meta.append(heroItem.year);
        if (heroItem.rating > 0f) {
            if (meta.length() > 0) meta.append("   •   ");
            meta.append("★ ").append(String.format(java.util.Locale.US, "%.1f", heroItem.rating));
        }
        if (!heroItem.genres.isEmpty()) {
            if (meta.length() > 0) meta.append("   •   ");
            meta.append(heroItem.genres);
        }
        heroMeta.setText(meta.toString());
        Glide.with(this).load(heroItem.backdropUrl.isEmpty()
                        ? heroItem.imageUrl : heroItem.backdropUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(heroBackdrop);
        heroBox.setVisibility(View.VISIBLE);
    }

    private void loadHomeExtras() {
        // Latest movies + latest series rows.
        tmdb.popular("movie", 1, new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLatestHeading(latestMoviesBox, getString(R.string.latest_movies));
                    latestMoviesAdapter.submit(items);
                    latestMoviesBox.setVisibility(feed == Feed.HOME && !items.isEmpty()
                            ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError() {
            }
        });
        tmdb.popular("tv", 1, new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLatestHeading(latestSeriesBox, getString(R.string.latest_series));
                    latestSeriesAdapter.submit(items);
                    latestSeriesBox.setVisibility(feed == Feed.HOME && !items.isEmpty()
                            ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError() {
            }
        });
        // Categories chips.
        tmdb.genreList("movie", new TmdbClient.Callback<List<TmdbClient.Genre>>() {
            @Override
            public void onSuccess(List<TmdbClient.Genre> genres) {
                runOnUiThread(() -> renderCategories(genres));
            }

            @Override
            public void onError() {
            }
        });
    }

    private void renderCategories(List<TmdbClient.Genre> genres) {
        categoriesRow.removeAllViews();
        if (genres == null || genres.isEmpty()) return;
        for (TmdbClient.Genre genre : genres) {
            Button chip = new Button(this);
            chip.setText(genre.name);
            chip.setAllCaps(false);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(television ? 14 : 12);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setFocusable(television);
            chip.setFocusableInTouchMode(television);
            chip.setBackground(pill(Color.rgb(29, 24, 40), dp(14), Color.rgb(75, 58, 96)));
            chip.setOnClickListener(v -> {
                genreMediaType = "movie";
                genreId = genre.id;
                genreName = genre.name;
                loadFeed(Feed.GENRE, "", genre.id);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(television ? 40 : 34));
            params.setMargins(dp(3), 0, dp(3), 0);
            categoriesRow.addView(chip, params);
        }
        if (categoriesRow.getParent() instanceof View) {
            ((View) categoriesRow.getParent()).setVisibility(View.VISIBLE);
        }
    }

    private void openWatch(CatalogItem item) {
        Intent intent = new Intent(this, WatchActivity.class);
        intent.putExtra("item", item);
        intent.putExtra("title", item.title);
        startActivity(intent);
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
