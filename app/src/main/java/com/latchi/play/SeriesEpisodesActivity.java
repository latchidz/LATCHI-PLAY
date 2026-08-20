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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Native season and episode browser for a public series page. */
public final class SeriesEpisodesActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private boolean television;
    private CatalogItem series;
    private CatalogClient client;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ProgressBar progress;
    private ContentStateView stateView;
    private LinearLayout seasonsRow;
    private final List<CatalogItem> allEpisodes = new ArrayList<>();
    private final List<CatalogItem> visibleEpisodes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        series = (CatalogItem) getIntent().getSerializableExtra("item");
        if (series == null || !"series".equals(series.type)) {
            finish();
            return;
        }
        client = new CatalogClient();
        buildUi();
        loadEpisodes();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(12), dp(6));
        header.setBackgroundColor(SURFACE);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(television ? 68 : 56)));

        Button back = seasonButton(getString(R.string.back));
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(television ? 110 : 78), -1));

        TextView title = new TextView(this);
        title.setText(series.title);
        title.setTextColor(GOLD);
        title.setTextSize(television ? 23 : 17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        title.setMaxLines(2);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonScroll.setFillViewport(true);
        seasonScroll.setBackgroundColor(Color.rgb(12, 10, 17));
        seasonsRow = new LinearLayout(this);
        seasonsRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonsRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        seasonsRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        seasonScroll.addView(seasonsRow, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(seasonScroll, new LinearLayout.LayoutParams(-1, dp(television ? 64 : 54)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(8), dp(television ? 24 : 4), dp(24));
        grid.setLayoutManager(new GridLayoutManager(this, DeviceUtils.catalogColumns(this, television)));
        grid.setItemAnimator(null);
        adapter = new PosterAdapter(television, this::openEpisode);
        grid.setAdapter(adapter);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        stateView.showMessage(getString(R.string.loading_episodes));
        grid.setVisibility(View.GONE);
        if (television) back.requestFocus();
    }

    private void loadEpisodes() {
        if (!DeviceUtils.hasInternetConnection(this)) {
            showError(getString(R.string.no_internet));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        stateView.showMessage(getString(R.string.loading_episodes));
        client.load(series.pageUrl, new CatalogClient.Callback() {
            @Override
            public void onSuccess(CatalogPage page) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    allEpisodes.clear();
                    for (CatalogItem item : page.items) {
                        if ("episode".equals(item.type)) allEpisodes.add(item);
                    }
                    if (allEpisodes.isEmpty()) {
                        grid.setVisibility(View.GONE);
                        stateView.showMessage(getString(R.string.no_episodes));
                        return;
                    }
                    buildSeasonButtons();
                });
            }

            @Override
            public void onError(CatalogClient.Failure failure) {
                runOnUiThread(() -> showError(
                        failure.type == CatalogClient.FailureType.NETWORK ||
                                failure.type == CatalogClient.FailureType.TIMEOUT
                                ? getString(R.string.no_internet)
                                : getString(R.string.load_content_failed)));
            }
        });
    }

    private void buildSeasonButtons() {
        seasonsRow.removeAllViews();
        Set<Integer> seasons = new TreeSet<>();
        for (CatalogItem episode : allEpisodes) seasons.add(Math.max(1, episode.seasonNumber));
        for (int season : seasons) {
            Button button = seasonButton(getString(R.string.season_number, season));
            button.setTag(season);
            button.setOnClickListener(view -> selectSeason(season));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(television ? 155 : 120), -1);
            params.setMargins(dp(4), 0, dp(4), 0);
            seasonsRow.addView(button, params);
        }
        selectSeason(seasons.iterator().next());
    }

    private void selectSeason(int season) {
        for (int index = 0; index < seasonsRow.getChildCount(); index++) {
            View child = seasonsRow.getChildAt(index);
            boolean selected = child.getTag() instanceof Integer && (Integer) child.getTag() == season;
            child.setBackground(buttonBackground(selected));
        }
        visibleEpisodes.clear();
        for (CatalogItem episode : allEpisodes) {
            if (Math.max(1, episode.seasonNumber) == season) visibleEpisodes.add(episode);
        }
        visibleEpisodes.sort(Comparator.comparingInt(item ->
                item.episodeNumber > 0 ? item.episodeNumber : Integer.MAX_VALUE));
        adapter.submit(visibleEpisodes);
        stateView.hide();
        grid.setVisibility(View.VISIBLE);
        if (television) grid.postDelayed(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
        }, 180);
    }

    private void openEpisode(CatalogItem episode) {
        int index = visibleEpisodes.indexOf(episode);
        CatalogItem next = index >= 0 && index + 1 < visibleEpisodes.size()
                ? visibleEpisodes.get(index + 1) : null;
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", episode);
        if (next != null) intent.putExtra("next_item", next);
        startActivity(intent);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        stateView.showAction(message, getString(R.string.retry), view -> loadEpisodes());
    }

    private Button seasonButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(television ? 15 : 12);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setFocusable(television);
        button.setFocusableInTouchMode(television);
        button.setBackground(buttonBackground(false));
        if (television) {
            button.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(buttonBackground(focused));
                view.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                        .setDuration(120).start();
            });
        }
        return button;
    }

    private GradientDrawable buttonBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? PURPLE : Color.rgb(31, 25, 42));
        drawable.setCornerRadius(dp(13));
        drawable.setStroke(dp(focused ? 2 : 1), focused ? GOLD : Color.rgb(75, 58, 96));
        return drawable;
    }

    @Override
    protected void onDestroy() {
        if (client != null) client.destroy();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
