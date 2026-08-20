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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Inline seasons and episodes shown directly inside a series details page. */
public final class SeriesEpisodesPanel extends LinearLayout {
    public interface Listener {
        void onReady(List<CatalogItem> episodes);
        void onEpisodeSelected(CatalogItem episode, CatalogItem nextEpisode);
    }

    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private final Activity activity;
    private final boolean television;
    private final CatalogItem series;
    private final Listener listener;
    private final CatalogClient client = new CatalogClient();
    private final LinearLayout seasonsRow;
    private final GridLayout episodesGrid;
    private final TextView status;
    private final List<CatalogItem> allEpisodes = new ArrayList<>();
    private final List<CatalogItem> visibleEpisodes = new ArrayList<>();

    public SeriesEpisodesPanel(Activity activity, boolean television, CatalogItem series, Listener listener) {
        super(activity);
        this.activity = activity;
        this.television = television;
        this.series = series;
        this.listener = listener;
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

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        seasonsRow = new LinearLayout(activity);
        seasonsRow.setOrientation(HORIZONTAL);
        seasonsRow.setGravity(Gravity.RIGHT);
        scroll.addView(seasonsRow, new HorizontalScrollView.LayoutParams(-2, dp(television ? 58 : 50)));
        addView(scroll, new LayoutParams(-1, dp(television ? 62 : 54)));

        episodesGrid = new GridLayout(activity);
        episodesGrid.setColumnCount(television ? 5 : 3);
        episodesGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        episodesGrid.setUseDefaultMargins(false);
        LayoutParams gridParams = new LayoutParams(-1, -2);
        gridParams.setMargins(0, dp(8), 0, 0);
        addView(episodesGrid, gridParams);
    }

    public void load() {
        if (!DeviceUtils.hasInternetConnection(activity)) {
            showRetry(activity.getString(R.string.no_internet));
            return;
        }
        status.setText(R.string.loading_episodes);
        client.load(series.pageUrl, new CatalogClient.Callback() {
            @Override
            public void onSuccess(CatalogPage page) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    allEpisodes.clear();
                    for (CatalogItem item : page.items) {
                        if ("episode".equals(item.type)) allEpisodes.add(item);
                    }
                    if (allEpisodes.isEmpty()) {
                        status.setText(R.string.no_episodes);
                        seasonsRow.removeAllViews();
                        episodesGrid.removeAllViews();
                        return;
                    }
                    buildSeasons();
                    listener.onReady(new ArrayList<>(allEpisodes));
                });
            }

            @Override
            public void onError(CatalogClient.Failure failure) {
                activity.runOnUiThread(() -> showRetry(
                        failure.type == CatalogClient.FailureType.NETWORK ||
                                failure.type == CatalogClient.FailureType.TIMEOUT
                                ? activity.getString(R.string.no_internet)
                                : activity.getString(R.string.load_content_failed)));
            }
        });
    }

    private void buildSeasons() {
        status.setVisibility(GONE);
        seasonsRow.removeAllViews();
        Set<Integer> seasons = new TreeSet<>();
        for (CatalogItem episode : allEpisodes) seasons.add(Math.max(1, episode.seasonNumber));
        for (int season : seasons) {
            Button button = makeButton(activity.getString(R.string.season_number, season));
            button.setTag(season);
            button.setOnClickListener(view -> selectSeason(season));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(television ? 145 : 112), dp(television ? 52 : 46));
            params.setMargins(dp(4), 0, dp(4), 0);
            seasonsRow.addView(button, params);
        }
        selectSeason(seasons.iterator().next());
    }

    private void selectSeason(int season) {
        for (int index = 0; index < seasonsRow.getChildCount(); index++) {
            View child = seasonsRow.getChildAt(index);
            boolean selected = child.getTag() instanceof Integer && (Integer) child.getTag() == season;
            child.setBackground(background(selected));
        }
        visibleEpisodes.clear();
        for (CatalogItem episode : allEpisodes) {
            if (Math.max(1, episode.seasonNumber) == season) visibleEpisodes.add(episode);
        }
        visibleEpisodes.sort(Comparator.comparingInt(item ->
                item.episodeNumber > 0 ? item.episodeNumber : Integer.MAX_VALUE));
        buildEpisodeButtons();
    }

    private void buildEpisodeButtons() {
        episodesGrid.removeAllViews();
        int columns = television ? 5 : 3;
        for (int index = 0; index < visibleEpisodes.size(); index++) {
            CatalogItem episode = visibleEpisodes.get(index);
            CatalogItem next = index + 1 < visibleEpisodes.size() ? visibleEpisodes.get(index + 1) : null;
            String label = episode.episodeNumber > 0
                    ? activity.getString(R.string.episode_number, episode.episodeNumber)
                    : episode.title;
            Button button = makeButton(label);
            button.setContentDescription(episode.title);
            button.setOnClickListener(view -> listener.onEpisodeSelected(episode, next));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(television ? 58 : 52);
            params.columnSpec = GridLayout.spec(index % columns, 1f);
            params.rowSpec = GridLayout.spec(index / columns);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            episodesGrid.addView(button, params);
        }
    }

    private void showRetry(String message) {
        status.setVisibility(VISIBLE);
        status.setText(message);
        seasonsRow.removeAllViews();
        episodesGrid.removeAllViews();
        Button retry = makeButton(activity.getString(R.string.retry));
        retry.setOnClickListener(view -> load());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(190);
        params.height = dp(50);
        episodesGrid.addView(retry, params);
    }

    private Button makeButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(television ? 15 : 12);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setFocusable(television);
        button.setFocusableInTouchMode(television);
        button.setBackground(background(false));
        if (television) {
            button.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(background(focused));
                view.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                        .setDuration(120).start();
            });
        }
        return button;
    }

    private GradientDrawable background(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(active ? PURPLE : Color.rgb(31, 25, 42));
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(active ? 2 : 1), active ? GOLD : Color.rgb(75, 58, 96));
        return drawable;
    }

    public void destroy() {
        client.destroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
