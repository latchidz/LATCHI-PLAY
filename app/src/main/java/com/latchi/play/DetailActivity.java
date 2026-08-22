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

import java.util.ArrayList;
import java.util.List;

/**
 * Movie / series details from TMDB with inline seasons & episodes for series.
 * Playback goes through the provider registry + native ExoPlayer.
 */
public class DetailActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private CatalogItem item;
    private CatalogItem firstEpisode;
    private FavoritesStore favoritesStore;
    private SeriesEpisodesPanel episodesPanel;
    private TmdbClient tmdb;
    private boolean television;
    private ContentStateView stateView;
    private ProgressBar progress;
    private Button watchButton;
    private Button favoriteButton;
    private boolean seriesItem;

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
                        item.backdropUrl, item.genres, recoveredMedia);
            }
        }
        seriesItem = "series".equals(item.type) || "tv".equals(item.mediaType);
        favoritesStore = new FavoritesStore(this);
        tmdb = new TmdbClient(this);
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
        // Items from web-site providers have no TMDB id; render from their own data.
        if (item.tmdbId <= 0) {
            TmdbDetail synthetic = new TmdbDetail(0L,
                    seriesItem ? "tv" : "movie",
                    item.title, item.overview, item.rating, item.year, item.genres,
                    item.imageUrl, item.backdropUrl, 0,
                    new ArrayList<>());
            buildContent(synthetic);
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
                    buildContent(detail);
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

    private void buildContent(TmdbDetail detail) {
        stateView.hide();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1, -1);
        scrollParams.topMargin = dp(3);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(television ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setPadding(dp(television ? 42 : 18), dp(television ? 26 : 16),
                dp(television ? 42 : 18), dp(30));
        root.setGravity(television ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        String poster = detail.posterUrl.isEmpty() ? item.imageUrl : detail.posterUrl;
        ImageView posterView = new ImageView(this);
        posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterView.setBackground(round(Color.rgb(25, 20, 34), dp(20), Color.rgb(78, 56, 104)));
        posterView.setContentDescription(detail.title);
        posterView.setClipToOutline(true);
        Glide.with(this).load(poster).centerCrop().into(posterView);
        LinearLayout.LayoutParams posterParams = television
                ? new LinearLayout.LayoutParams(dp(300), dp(440))
                : new LinearLayout.LayoutParams(-1, dp(400));
        posterParams.setMargins(television ? dp(32) : 0, 0, 0, dp(18));
        root.addView(posterView, posterParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(television ? 28 : 4), dp(television ? 18 : 8),
                dp(television ? 28 : 4), dp(18));
        root.addView(info, television ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2));

        TextView badge = text(seriesItem ? getString(R.string.series_badge)
                : getString(R.string.movie_badge), television ? 16 : 13, GOLD, true);
        info.addView(badge, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(detail.title.isEmpty() ? item.title : detail.title,
                television ? 34 : 25, Color.WHITE, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(0, dp(10), 0, dp(4));
        info.addView(title, new LinearLayout.LayoutParams(-1, -2));

        String metaText = metaLine(detail);
        if (!metaText.isEmpty()) {
            TextView meta = text(metaText, television ? 17 : 14, GOLD, false);
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(4), 0, dp(10));
            info.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        }

        if (!detail.overview.isEmpty()) {
            TextView overview = text(detail.overview, television ? 18 : 15,
                    Color.rgb(216, 210, 224), false);
            overview.setGravity(Gravity.RIGHT);
            overview.setLineSpacing(0, 1.3f);
            overview.setPadding(0, dp(6), 0, dp(12));
            info.addView(overview, new LinearLayout.LayoutParams(-1, -2));
        }

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
                television ? dp(280) : -1, dp(television ? 64 : 56));
        watchParams.setMargins(0, dp(24), 0, dp(10));
        info.addView(watchButton, watchParams);

        favoriteButton = button(favoriteLabel(favoritesStore.isFavorite(item)),
                Color.rgb(34, 28, 45));
        favoriteButton.setOnClickListener(view -> {
            boolean favorite = favoritesStore.toggle(item);
            favoriteButton.setText(favoriteLabel(favorite));
        });
        info.addView(favoriteButton, new LinearLayout.LayoutParams(television ? dp(280) : -1,
                dp(television ? 58 : 52)));

        Button back = button(getString(R.string.back), Color.rgb(24, 20, 32));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                television ? dp(180) : -1, dp(television ? 54 : 48));
        backParams.setMargins(0, dp(10), 0, 0);
        info.addView(back, backParams);

        if (seriesItem && item.tmdbId <= 0) {
            TextView note = text(getString(R.string.site_series_note),
                    television ? 15 : 13, Color.rgb(175, 167, 190), false);
            note.setGravity(Gravity.RIGHT);
            note.setPadding(0, dp(14), 0, dp(6));
            info.addView(note, new LinearLayout.LayoutParams(-1, -2));
        } else if (seriesItem) {
            episodesPanel = new SeriesEpisodesPanel(this, television, item.tmdbId,
                    detail.seasons, new SeriesEpisodesPanel.Listener() {
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
            info.addView(episodesPanel, new LinearLayout.LayoutParams(-1, -2));
        }
        if (television && !seriesItem) watchButton.requestFocus();
    }

    private String metaLine(TmdbDetail detail) {
        StringBuilder line = new StringBuilder();
        if (!detail.year.isEmpty()) line.append(detail.year);
        if (detail.rating > 0f) {
            if (line.length() > 0) line.append("  •  ");
            line.append("★ ").append(String.format(java.util.Locale.US, "%.1f", detail.rating));
        }
        if (!detail.genres.isEmpty()) {
            if (line.length() > 0) line.append("  •  ");
            line.append(detail.genres);
        }
        if (detail.runtimeMinutes > 0) {
            if (line.length() > 0) line.append("  •  ");
            line.append(detail.runtimeMinutes).append(" ").append(getString(R.string.minutes));
        }
        return line.toString();
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
        if (episodesPanel != null) episodesPanel.destroy();
        if (tmdb != null) tmdb.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
