package com.latchi.play;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Inline seasons and episodes inside a series details page, driven by TMDB.
 */
public final class SeriesEpisodesPanel extends LinearLayout {
    public interface Listener {
        void onReady(List<CatalogItem> episodes);
        void onEpisodeSelected(CatalogItem episode, CatalogItem nextEpisode);
    }

    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int SURFACE = Color.rgb(22, 18, 31);

    private final Activity activity;
    private final boolean television;
    private final long tvId;
    private final List<TmdbDetail.TmdbSeason> seasons;
    private final Listener listener;
    private final TmdbClient tmdb;
    private final LinearLayout seasonsRow;
    private final GridLayout episodesGrid;
    private final TextView status;
    private final List<Button> seasonButtons = new ArrayList<>();
    private final List<CatalogItem> allEpisodes = new ArrayList<>();
    private final List<CatalogItem> currentSeasonEpisodes = new ArrayList<>();
    private int activeSeason = -1;

    public SeriesEpisodesPanel(Activity activity, boolean television, long tvId,
                               List<TmdbDetail.TmdbSeason> seasons, Listener listener) {
        super(activity);
        this.activity = activity;
        this.television = television;
        this.tvId = tvId;
        this.seasons = seasons == null ? new ArrayList<>() : seasons;
        this.listener = listener;
        this.tmdb = new TmdbClient(activity);

        setOrientation(VERTICAL);
        setPadding(0, dp(20), 0, dp(10));

        TextView heading = new TextView(activity);
        heading.setText(R.string.seasons_and_episodes);
        heading.setTextColor(GOLD);
        heading.setTextSize(television ? 22 : 18);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setGravity(Gravity.RIGHT);
        addView(heading, new LayoutParams(-1, -2));

        status = new TextView(activity);
        status.setText(R.string.loading_episodes);
        status.setTextColor(Color.rgb(190, 182, 200));
        status.setTextSize(television ? 16 : 14);
        status.setGravity(Gravity.RIGHT);
        LayoutParams statusParams = new LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(10), 0, dp(10));
        addView(status, statusParams);

        seasonsRow = new LinearLayout(activity);
        seasonsRow.setOrientation(HORIZONTAL);
        seasonsRow.setGravity(Gravity.RIGHT);
        episodesGrid = new GridLayout(activity);
        episodesGrid.setColumnCount(television ? 5 : 3);
        episodesGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        episodesGrid.setUseDefaultMargins(false);

        if (this.seasons.isEmpty()) {
            status.setText(R.string.no_episodes);
            return;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(seasonsRow, new HorizontalScrollView.LayoutParams(-2, dp(television ? 58 : 50)));
        addView(scroll, new LayoutParams(-1, dp(television ? 62 : 54)));

        LayoutParams gridParams = new LayoutParams(-1, -2);
        gridParams.setMargins(0, dp(8), 0, 0);
        addView(episodesGrid, gridParams);

        renderSeasons();
        selectSeason(this.seasons.get(0).seasonNumber);
    }

    private void renderSeasons() {
        seasonButtons.clear();
        for (TmdbDetail.TmdbSeason season : seasons) {
            Button chip = new Button(activity);
            chip.setText(getStringSeason(season.seasonNumber));
            chip.setAllCaps(false);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(television ? 15 : 13);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setFocusable(television);
            chip.setFocusableInTouchMode(television);
            chip.setOnClickListener(v -> selectSeason(season.seasonNumber));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(television ? 130 : 104), -1);
            params.setMargins(dp(4), 0, dp(4), 0);
            seasonsRow.addView(chip, params);
            seasonButtons.add(chip);
        }
        updateSeasonHighlight();
    }

    private void selectSeason(int seasonNumber) {
        activeSeason = seasonNumber;
        updateSeasonHighlight();
        currentSeasonEpisodes.clear();
        episodesGrid.removeAllViews();
        status.setText(R.string.loading_episodes);

        tmdb.episodes(tvId, seasonNumber, new TmdbClient.Callback<List<CatalogItem>>() {
            @Override
            public void onSuccess(List<CatalogItem> episodes) {
                activity.runOnUiThread(() -> {
                    if (activeSeason != seasonNumber || episodes.isEmpty()) {
                        if (activeSeason == seasonNumber) {
                            status.setText(R.string.no_episodes);
                        }
                        return;
                    }
                    status.setText(getStringSeason(seasonNumber));
                    currentSeasonEpisodes.addAll(episodes);
                    allEpisodes.clear();
                    allEpisodes.addAll(currentSeasonEpisodes);
                    renderEpisodes(episodes);
                    listener.onReady(new ArrayList<>(allEpisodes));
                });
            }

            @Override
            public void onError() {
                activity.runOnUiThread(() -> {
                    if (activeSeason == seasonNumber) {
                        status.setText(R.string.no_episodes);
                    }
                });
            }
        });
    }

    private void renderEpisodes(List<CatalogItem> episodes) {
        episodesGrid.removeAllViews();
        for (int i = 0; i < episodes.size(); i++) {
            final CatalogItem episode = episodes.get(i);
            final CatalogItem next = nextEpisode(i);
            Button button = new Button(activity);
            button.setText(episodeTitle(episode));
            button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setTextSize(television ? 14 : 12);
            button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            button.setGravity(Gravity.CENTER);
            button.setFocusable(television);
            button.setFocusableInTouchMode(television);
            button.setBackground(round(SURFACE, dp(10), GOLD));
            if (television) {
                button.setOnFocusChangeListener((v, focused) ->
                        v.setBackground(round(focused ? PURPLE : SURFACE, dp(10),
                                focused ? GOLD : Color.rgb(68, 54, 86))));
            }
            button.setOnClickListener(v -> listener.onEpisodeSelected(episode, next));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(television ? 132 : 96);
            params.height = dp(television ? 52 : 44);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            episodesGrid.addView(button, params);
        }
    }

    private CatalogItem nextEpisode(int currentIndex) {
        if (currentIndex + 1 < currentSeasonEpisodes.size()) {
            return currentSeasonEpisodes.get(currentIndex + 1);
        }
        // Last episode of the season: no automatic next item (user picks the next season).
        return null;
    }

    private void updateSeasonHighlight() {
        for (int i = 0; i < seasonButtons.size(); i++) {
            Button chip = seasonButtons.get(i);
            boolean active = seasons.get(i).seasonNumber == activeSeason;
            chip.setBackground(round(active ? PURPLE : Color.rgb(29, 24, 40), dp(12),
                    active ? GOLD : Color.rgb(68, 54, 86)));
        }
    }

    private String episodeTitle(CatalogItem episode) {
        String name = episode.title.trim();
        if (name.isEmpty()) return getString(R.string.episode_number, episode.episodeNumber);
        return getString(R.string.episode_number, episode.episodeNumber) + " • " + name;
    }

    private String getStringSeason(int seasonNumber) {
        return activity.getString(R.string.season_number, seasonNumber);
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    public void destroy() {
        tmdb.destroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
