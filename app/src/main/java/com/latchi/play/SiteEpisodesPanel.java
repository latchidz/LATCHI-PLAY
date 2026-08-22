package com.latchi.play;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Seasons + episodes for a site provider, normalized and numerically ordered,
 * rendered as premium episode cards (thumbnail, number, title, progress).
 */
public final class SiteEpisodesPanel extends LinearLayout {
    public interface Listener {
        void onReady(List<CatalogItem> episodes);
        void onEpisodeSelected(CatalogItem episode, CatalogItem nextEpisode);
    }

    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int SURFACE = Color.rgb(22, 18, 31);

    private final Activity activity;
    private final boolean television;
    private final ContentProvider provider;
    private final CatalogItem series;
    private final Listener listener;
    private final HistoryStore historyStore;
    private final LinearLayout seasonsRow;
    private final GridLayout episodesGrid;
    private final TextView status;
    private final List<Button> seasonButtons = new ArrayList<>();
    private final List<SeasonGroup> seasons = new ArrayList<>();
    private final List<CatalogItem> currentSeasonEpisodes = new ArrayList<>();
    private int activeSeason = -1;

    public SiteEpisodesPanel(Activity activity, boolean television, ContentProvider provider,
                             CatalogItem series, Listener listener) {
        super(activity);
        this.activity = activity;
        this.television = television;
        this.provider = provider;
        this.series = series;
        this.listener = listener;
        this.historyStore = new HistoryStore(activity);

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

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(seasonsRow, new HorizontalScrollView.LayoutParams(-2, dp(television ? 58 : 50)));
        addView(scroll, new LayoutParams(-1, dp(television ? 62 : 54)));

        LayoutParams gridParams = new LayoutParams(-1, -2);
        gridParams.setMargins(0, dp(8), 0, 0);
        addView(episodesGrid, gridParams);

        load();
    }

    private void load() {
        status.setText(R.string.loading_episodes);
        provider.episodes(series, new ContentProvider.EpisodesCallback() {
            @Override
            public void onSuccess(List<SeasonGroup> groups) {
                activity.runOnUiThread(() -> {
                    if (isFinishing()) return;
                    seasons.clear();
                    seasons.addAll(groups);
                    seasons.sort(Comparator.comparingInt(g -> g.seasonNumber));
                    if (seasons.isEmpty()) {
                        status.setText(R.string.no_episodes);
                        return;
                    }
                    renderSeasons();
                    selectSeason(seasons.get(0).seasonNumber);
                });
            }

            @Override
            public void onError() {
                activity.runOnUiThread(() -> {
                    if (!isFinishing()) status.setText(R.string.no_episodes);
                });
            }
        });
    }

    private void renderSeasons() {
        seasonsRow.removeAllViews();
        seasonButtons.clear();
        for (SeasonGroup season : seasons) {
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
        for (SeasonGroup season : seasons) {
            if (season.seasonNumber == seasonNumber) {
                currentSeasonEpisodes.addAll(season.episodes);
                break;
            }
        }
        if (currentSeasonEpisodes.isEmpty()) {
            status.setText(R.string.no_episodes);
            return;
        }
        status.setText(getStringSeason(seasonNumber));
        renderEpisodes(currentSeasonEpisodes);
        listener.onReady(new ArrayList<>(currentSeasonEpisodes));
    }

    private void renderEpisodes(List<CatalogItem> episodes) {
        episodesGrid.removeAllViews();
        for (int i = 0; i < episodes.size(); i++) {
            final CatalogItem episode = episodes.get(i);
            final CatalogItem next = nextEpisode(i);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(VERTICAL);
            card.setFocusable(television);
            card.setFocusableInTouchMode(television);
            card.setClickable(true);
            card.setClipToOutline(true);
            card.setBackground(cardBackground(false));

            FrameLayout imageBox = new FrameLayout(activity);
            ImageView thumb = new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(Color.rgb(12, 10, 17));
            int thumbWidth = dp(television ? 168 : 108);
            int thumbHeight = dp(television ? 96 : 62);
            imageBox.addView(thumb, new FrameLayout.LayoutParams(thumbWidth, thumbHeight));
            if (episode.imageUrl != null && !episode.imageUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(activity)
                        .load(episode.imageUrl)
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .centerCrop()
                        .into(thumb);
            }
            ProgressBar progressBar = new ProgressBar(activity, null,
                    android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(watchedProgress(episode));
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1,
                    dp(3), Gravity.BOTTOM);
            imageBox.addView(progressBar, progressParams);
            card.addView(imageBox, new LayoutParams(thumbWidth, thumbHeight));

            TextView label = new TextView(activity);
            label.setText(episodeTitle(episode));
            label.setTextColor(Color.WHITE);
            label.setTextSize(television ? 13 : 11);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setPadding(dp(4), dp(5), dp(4), dp(5));
            card.addView(label, new LayoutParams(-1, -2));

            if (television) {
                card.setOnFocusChangeListener((v, focused) -> {
                    v.setBackground(cardBackground(focused));
                    v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                            .setDuration(140).start();
                });
            }
            card.setOnClickListener(v -> listener.onEpisodeSelected(episode, next));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = thumbWidth;
            params.height = LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(4), dp(6), dp(4), dp(6));
            episodesGrid.addView(card, params);
        }
    }

    private int watchedProgress(CatalogItem episode) {
        List<HistoryEntry> all = historyStore.getAll();
        for (HistoryEntry entry : all) {
            if (entry.item.pageUrl.equals(episode.pageUrl)) {
                return entry.progressPercent();
            }
        }
        return 0;
    }

    private CatalogItem nextEpisode(int currentIndex) {
        if (currentIndex + 1 < currentSeasonEpisodes.size()) {
            return currentSeasonEpisodes.get(currentIndex + 1);
        }
        for (int s = 0; s < seasons.size(); s++) {
            if (seasons.get(s).seasonNumber == activeSeason && s + 1 < seasons.size()) {
                SeasonGroup nextSeason = seasons.get(s + 1);
                if (!nextSeason.episodes.isEmpty()) return nextSeason.episodes.get(0);
            }
        }
        return null;
    }

    private String episodeTitle(CatalogItem episode) {
        String title = episode.title.trim();
        if (title.isEmpty()) return getStringSeason(episode.seasonNumber) + " • " +
                activity.getString(R.string.episode_number, episode.episodeNumber);
        return activity.getString(R.string.episode_number, episode.episodeNumber) + " • " + title;
    }

    private void updateSeasonHighlight() {
        for (int i = 0; i < seasonButtons.size(); i++) {
            Button chip = seasonButtons.get(i);
            boolean active = seasons.get(i).seasonNumber == activeSeason;
            chip.setBackground(round(active ? Color.rgb(124, 58, 237) : Color.rgb(29, 24, 40),
                    dp(12), active ? GOLD : Color.rgb(68, 54, 86)));
        }
    }

    private String getStringSeason(int seasonNumber) {
        return activity.getString(R.string.season_number, seasonNumber);
    }

    private GradientDrawable cardBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? Color.rgb(37, 29, 50) : SURFACE);
        d.setCornerRadius(dp(10));
        d.setStroke(focused ? 3 : 1, focused ? GOLD : Color.rgb(68, 54, 86));
        return d;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private boolean isFinishing() {
        return activity.isFinishing() || activity.isDestroyed();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
