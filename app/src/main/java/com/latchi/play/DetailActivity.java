package com.latchi.play;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Cinematic details screen. Works for TMDB items and for site-provider items
 * (details + seasons/episodes come from the provider). Playback always goes
 * through the provider registry + native ExoPlayer.
 */
public class DetailActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private CatalogItem item;
    private CatalogItem firstEpisode;
    private FavoritesStore favoritesStore;
    private TmdbClient tmdb;
    private ContentProvider siteProvider;
    private boolean television;
    private boolean seriesItem;
    private ContentStateView stateView;
    private ProgressBar progress;
    private Button watchButton;
    private Button favoriteButton;
    private SeriesEpisodesPanel tmdbEpisodesPanel;
    private SiteEpisodesPanel siteEpisodesPanel;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        item = (CatalogItem) getIntent().getSerializableExtra("item");
        if (item == null) {
            finish();
            return;
        }
        if (item.tmdbId <= 0) {
            long recoveredId = CatalogItem.tmdbIdFromPageUrl(item.pageUrl);
            String recoveredMedia = CatalogItem.mediaTypeFromPageUrl(item.pageUrl);
            if (recoveredId > 0) {
                item = new CatalogItem(item.title, item.imageUrl, item.pageUrl,
                        "tv".equals(recoveredMedia) ? "series" : "movie",
                        item.seasonNumber, item.episodeNumber, item.metadata,
                        recoveredId, item.overview, item.rating, item.year,
                        item.backdropUrl, item.genres, recoveredMedia,
                        item.providerId, item.contentId);
            }
        }
        seriesItem = "series".equals(item.type) || "tv".equals(item.mediaType);
        favoritesStore = new FavoritesStore(this);
        tmdb = new TmdbClient(this);
        if (item.tmdbId <= 0 && !item.providerId.isEmpty()) {
            siteProvider = ProviderRegistry.byId(this, item.providerId);
        }
        buildChrome();
        loadDetails();
    }

    private void buildChrome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(3)));

        stateView = new ContentStateView(this, television);
        root.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        stateView.showMessage(getString(R.string.loading_details));
    }

    private void loadDetails() {
        // Site-provider item (no TMDB id): ask the provider for rich details.
        if (item.tmdbId <= 0 && siteProvider != null) {
            progress.setVisibility(View.VISIBLE);
            siteProvider.details(item, new ContentProvider.DetailsCallback() {
                @Override
                public void onSuccess(MediaDetail detail) {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        if (isFinishing() || isDestroyed()) return;
                        buildContent(title(detail), poster(detail), backdrop(detail),
                                metaLine(detail.year, detail.rating, detail.genres,
                                        detail.durationMinutes, detail.ratingCount),
                                detail.description, detail.genres, detail.cast,
                                detail.director);
                        attachSiteEpisodes();
                    });
                }

                @Override
                public void onError() {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        if (isFinishing() || isDestroyed()) return;
                        buildContent(item.title, item.imageUrl, item.backdropUrl,
                                metaLine(item.year, item.rating, item.genres, 0, 0),
                                item.overview, item.genres, null, "");
                        attachSiteEpisodes();
                    });
                }
            });
            return;
        }
        if (item.tmdbId <= 0) {
            buildContent(item.title, item.imageUrl, item.backdropUrl, "", item.overview,
                    item.genres, null, "");
            attachSiteEpisodes();
            return;
        }
        if (!tmdb.isConfigured()) {
            stateView.showAction(getString(R.string.tmdb_key_missing),
                    getString(R.string.open_settings),
                    v -> startActivity(new Intent(this, SettingsActivity.class)));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        tmdb.details(item, new TmdbClient.Callback<TmdbDetail>() {
            @Override
            public void onSuccess(TmdbDetail detail) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (isFinishing() || isDestroyed()) return;
                    buildContent(detail.title.isEmpty() ? item.title : detail.title,
                            detail.posterUrl.isEmpty() ? item.imageUrl : detail.posterUrl,
                            detail.backdropUrl.isEmpty() ? item.backdropUrl : detail.backdropUrl,
                            metaLine(detail.year, detail.rating, detail.genres,
                                    detail.runtimeMinutes, 0),
                            detail.overview, detail.genres, null, "");
                    attachSeasons(detail.seasons);
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    stateView.showAction(getString(R.string.load_content_failed),
                            getString(R.string.retry), v -> loadDetails());
                });
            }
        });
    }

    private String title(MediaDetail detail) {
        return detail.title.isEmpty() ? item.title : detail.title;
    }

    private String poster(MediaDetail detail) {
        return detail.posterUrl.isEmpty() ? item.imageUrl : detail.posterUrl;
    }

    private String backdrop(MediaDetail detail) {
        if (!detail.backdropUrl.isEmpty()) return detail.backdropUrl;
        return detail.posterUrl.isEmpty() ? item.backdropUrl : detail.posterUrl;
    }

    private String metaLine(String year, float rating, String genres, int runtimeMinutes,
                            int ratingCount) {
        StringBuilder line = new StringBuilder();
        if (year != null && !year.isEmpty()) line.append(year);
        if (rating > 0f) {
            if (line.length() > 0) line.append("   •   ");
            line.append("★ ").append(String.format(java.util.Locale.US, "%.1f", rating));
        }
        if (ratingCount > 0) {
            line.append("  (").append(ratingCount).append(")");
        }
        if (runtimeMinutes > 0) {
            if (line.length() > 0) line.append("   •   ");
            line.append(runtimeMinutes).append(" ").append(getString(R.string.minutes));
        }
        if (genres != null && !genres.isEmpty()) {
            if (line.length() > 0) line.append("   •   ");
            line.append(genres);
        }
        return line.toString();
    }

    private void buildContent(String title, String posterUrl, String backdropUrl,
                              String meta, String description, String genres,
                              List<String> cast, String director) {
        stateView.hide();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        // ---------------- Header (backdrop + gradient + info) ----------------
        FrameLayout header = new FrameLayout(this);
        int headerHeight = dp(television ? 470 : 320);
        root.addView(header, new LinearLayout.LayoutParams(-1, headerHeight));

        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackgroundColor(Color.rgb(16, 13, 24));
        header.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        Glide.with(this).load(backdropUrl)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(backdrop);

        View gradient = new View(this);
        gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(60, 0, 0, 0), Color.rgb(7, 6, 12)}));
        header.addView(gradient, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout headerInfo = new LinearLayout(this);
        headerInfo.setOrientation(LinearLayout.VERTICAL);
        headerInfo.setGravity(Gravity.RIGHT);
        headerInfo.setPadding(dp(television ? 42 : 18), dp(24), dp(television ? 42 : 18), dp(18));
        header.addView(headerInfo, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView badge = text(seriesItem ? getString(R.string.series_badge)
                : getString(R.string.movie_badge), television ? 15 : 12, GOLD, true);
        headerInfo.addView(badge, new LinearLayout.LayoutParams(-1, -2));

        TextView titleView = text(title, television ? 38 : 26, Color.WHITE, true);
        titleView.setGravity(Gravity.RIGHT);
        titleView.setPadding(0, dp(6), 0, dp(4));
        headerInfo.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        if (meta != null && !meta.isEmpty()) {
            TextView metaView = text(meta, television ? 17 : 13, Color.rgb(214, 208, 224), false);
            metaView.setGravity(Gravity.RIGHT);
            metaView.setPadding(0, dp(2), 0, dp(10));
            headerInfo.addView(metaView, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        headerInfo.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        watchButton = button(seriesItem ? getString(R.string.play_first_episode)
                : getString(R.string.watch_now), PURPLE);
        if (seriesItem) {
            watchButton.setEnabled(false);
            watchButton.setAlpha(.55f);
            watchButton.setOnClickListener(view -> {
                if (firstEpisode != null) openEpisode(firstEpisode, null);
            });
        } else {
            watchButton.setOnClickListener(view -> openEpisode(item, null));
        }
        LinearLayout.LayoutParams watchParams = new LinearLayout.LayoutParams(
                television ? dp(280) : 0, dp(television ? 64 : 54), television ? 0 : 1);
        watchParams.setMargins(0, dp(8), 0, 0);
        buttons.addView(watchButton, watchParams);

        favoriteButton = button(favoriteLabel(favoritesStore.isFavorite(item)),
                Color.rgb(34, 28, 45));
        favoriteButton.setOnClickListener(view -> {
            boolean favorite = favoritesStore.toggle(item);
            favoriteButton.setText(favoriteLabel(favorite));
        });
        LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(
                television ? dp(240) : 0, dp(television ? 64 : 54), television ? 0 : 1);
        favParams.setMargins(television ? dp(14) : 0, dp(8), 0, 0);
        buttons.addView(favoriteButton, favParams);

        // ---------------- Body ----------------
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.RIGHT);
        body.setPadding(dp(television ? 42 : 18), dp(television ? 30 : 20),
                dp(television ? 42 : 18), dp(40));
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));

        if (description != null && !description.isEmpty()) {
            addSection(body, getString(R.string.story));
            TextView story = text(description, television ? 18 : 15,
                    Color.rgb(216, 210, 224), false);
            story.setGravity(Gravity.RIGHT);
            story.setLineSpacing(0, 1.35f);
            body.addView(story, new LinearLayout.LayoutParams(-1, -2));
        }

        if (cast != null && !cast.isEmpty()) {
            addSection(body, getString(R.string.cast));
            TextView castView = text(String.join("  •  ", cast), television ? 17 : 14,
                    Color.rgb(190, 182, 200), false);
            castView.setGravity(Gravity.RIGHT);
            body.addView(castView, new LinearLayout.LayoutParams(-1, -2));
        }
        if (director != null && !director.isEmpty()) {
            addSection(body, getString(R.string.director));
            TextView directorView = text(director, television ? 17 : 14,
                    Color.rgb(190, 182, 200), false);
            directorView.setGravity(Gravity.RIGHT);
            body.addView(directorView, new LinearLayout.LayoutParams(-1, -2));
        }

        // Back button for TV/home convenience.
        Button back = button(getString(R.string.back), Color.rgb(24, 20, 32));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                television ? dp(180) : -1, dp(television ? 54 : 48));
        backParams.setMargins(0, dp(6), 0, 0);
        body.addView(back, backParams);

        if (television && !seriesItem) watchButton.requestFocus();
    }

    private void addSection(LinearLayout body, String heading) {
        TextView label = text(heading, television ? 20 : 17, GOLD, true);
        label.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(18), 0, dp(8));
        body.addView(label, params);
    }

    /** Attaches TMDB seasons/episodes (series items from TMDB). */
    private void attachSeasons(List<TmdbDetail.TmdbSeason> seasons) {
        if (!seriesItem || body == null) return;
        tmdbEpisodesPanel = new SeriesEpisodesPanel(this, television, item.tmdbId,
                seasons, new SeriesEpisodesPanel.Listener() {
            @Override
            public void onReady(List<CatalogItem> episodes) {
                firstEpisode = null;
                for (CatalogItem episode : episodes) {
                    if (firstEpisode == null || compareEpisodes(episode, firstEpisode) < 0) {
                        firstEpisode = episode;
                    }
                }
                if (firstEpisode != null) {
                    watchButton.setEnabled(true);
                    watchButton.setAlpha(1f);
                    if (television) watchButton.requestFocus();
                }
            }

            @Override
            public void onEpisodeSelected(CatalogItem episode, CatalogItem nextEpisode) {
                openEpisode(episode, nextEpisode);
            }
        });
        body.addView(tmdbEpisodesPanel, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Attaches site-provider seasons/episodes. */
    private void attachSiteEpisodes() {
        if (!seriesItem || siteProvider == null || body == null) return;
        siteEpisodesPanel = new SiteEpisodesPanel(this, television, siteProvider, item,
                new SiteEpisodesPanel.Listener() {
            @Override
            public void onReady(List<CatalogItem> episodes) {
                firstEpisode = null;
                for (CatalogItem episode : episodes) {
                    if (firstEpisode == null || compareEpisodes(episode, firstEpisode) < 0) {
                        firstEpisode = episode;
                    }
                }
                if (firstEpisode != null) {
                    watchButton.setEnabled(true);
                    watchButton.setAlpha(1f);
                    if (television) watchButton.requestFocus();
                }
            }

            @Override
            public void onEpisodeSelected(CatalogItem episode, CatalogItem nextEpisode) {
                openEpisode(episode, nextEpisode);
            }
        });
        body.addView(siteEpisodesPanel, new LinearLayout.LayoutParams(-1, -2));
    }

    private int compareEpisodes(CatalogItem left, CatalogItem right) {
        int seasonCompare = Integer.compare(Math.max(1, left.seasonNumber), Math.max(1, right.seasonNumber));
        if (seasonCompare != 0) return seasonCompare;
        int leftEpisode = left.episodeNumber > 0 ? left.episodeNumber : Integer.MAX_VALUE;
        int rightEpisode = right.episodeNumber > 0 ? right.episodeNumber : Integer.MAX_VALUE;
        return Integer.compare(leftEpisode, rightEpisode);
    }

    private void openEpisode(CatalogItem episode, CatalogItem nextEpisode) {
        Intent intent = new Intent(this, WatchActivity.class);
        intent.putExtra("item", episode);
        intent.putExtra("title", episode.title);
        if (nextEpisode != null) intent.putExtra("next_item", nextEpisode);
        startActivity(intent);
    }

    private String favoriteLabel(boolean favorite) {
        return getString(favorite ? R.string.favorite_saved : R.string.favorite_add);
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(television ? 17 : 15);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setFocusable(television);
        b.setFocusableInTouchMode(television);
        b.setBackground(round(color, dp(15), GOLD));
        if (television) {
            b.setOnFocusChangeListener((v, focused) -> v.animate()
                    .scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(120).start());
        }
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

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    @Override
    protected void onDestroy() {
        if (tmdbEpisodesPanel != null) tmdbEpisodesPanel.destroy();
        if (tmdb != null) tmdb.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
