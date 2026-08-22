package com.latchi.play;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

import java.util.List;

/**
 * Native playback screen. Sources come from the provider registry as direct media
 * URLs (mp4 / m3u8) and are played with Media3 ExoPlayer — no WebView, no iframes.
 * Providers fail over automatically until one works.
 */
public class WatchActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private FrameLayout root;
    private LinearLayout chrome;
    private LinearLayout topBar;
    private PlayerView playerView;
    private PlaybackController playbackController;
    private HistoryStore historyStore;
    private ProgressBar progress;
    private ContentStateView stateView;
    private TextView sourceLabel;
    private TextView titleView;
    private Button nextButton;

    private CatalogItem currentItem;
    private CatalogItem nextItem;
    private List<ContentProvider> providers;
    private int providerIndex;
    private int resolveGeneration;
    private String activeProvider = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        currentItem = (CatalogItem) getIntent().getSerializableExtra("item");
        nextItem = (CatalogItem) getIntent().getSerializableExtra("next_item");
        String fallbackTitle = getIntent().getStringExtra("title");
        if (currentItem == null) {
            finish();
            return;
        }
        if (fallbackTitle != null && !fallbackTitle.isEmpty() && currentItem.title.isEmpty()) {
            currentItem = new CatalogItem(fallbackTitle, currentItem.imageUrl, currentItem.pageUrl,
                    currentItem.type, currentItem.seasonNumber, currentItem.episodeNumber,
                    currentItem.metadata, currentItem.tmdbId, currentItem.overview,
                    currentItem.rating, currentItem.year, currentItem.backdropUrl,
                    currentItem.genres, currentItem.mediaType);
        }

        historyStore = new HistoryStore(this);
        historyStore.markOpened(currentItem);
        buildUi();
        providers = ProviderRegistry.ordered(this);

        String suppliedUrl = getIntent().getStringExtra("direct_url");
        String suppliedType = getIntent().getStringExtra("direct_type");
        if (suppliedUrl != null && !suppliedUrl.trim().isEmpty()) {
            resolveGeneration++;
            startPlayback(new PlaybackSource(suppliedUrl, suppliedType == null ? "mp4" : suppliedType,
                    java.util.Collections.emptyMap(),
                    java.util.Collections.singletonMap("origin", "intent")), "مباشر");
        } else {
            resolveNextProvider(0);
        }
    }

    private void buildUi() {
        boolean television = DeviceUtils.isTelevision(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        root.addView(chrome, new FrameLayout.LayoutParams(-1, -1));

        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(4), dp(12), dp(4));
        topBar.setBackgroundColor(Color.rgb(13, 10, 18));
        chrome.addView(topBar, new LinearLayout.LayoutParams(-1, dp(50)));

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setFocusable(television);
        back.setOnClickListener(view -> onBackPressed());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(90), -1));

        titleView = new TextView(this);
        titleView.setText(currentItem.title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1));

        sourceLabel = new TextView(this);
        sourceLabel.setText("");
        sourceLabel.setTextColor(Color.rgb(175, 167, 190));
        sourceLabel.setTextSize(13);
        sourceLabel.setGravity(Gravity.CENTER);
        sourceLabel.setMaxLines(1);
        topBar.addView(sourceLabel, new LinearLayout.LayoutParams(dp(170), -1));

        TextView brand = new TextView(this);
        brand.setText(R.string.app_name);
        brand.setTextColor(GOLD);
        brand.setTextSize(14);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        topBar.addView(brand, new LinearLayout.LayoutParams(dp(130), -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        chrome.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        chrome.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        playerView.setVisibility(View.GONE);
        playerView.setFocusable(television);
        content.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        playbackController = new PlaybackController(this, playerView);

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        stateView.showMessage(getString(R.string.searching_source, getString(R.string.provider_archive)));

        nextButton = new Button(this);
        nextButton.setText(R.string.play_next_episode);
        nextButton.setAllCaps(false);
        nextButton.setTextColor(Color.WHITE);
        nextButton.setTextSize(television ? 18 : 16);
        nextButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nextButton.setBackground(rounded(PURPLE, dp(16), GOLD));
        nextButton.setVisibility(View.GONE);
        nextButton.setOnClickListener(v -> openNext());
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(
                dp(television ? 320 : 240), dp(television ? 64 : 56),
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        nextParams.bottomMargin = dp(40);
        content.addView(nextButton, nextParams);

        if (television) back.requestFocus();
    }

    private void resolveNextProvider(final int fromIndex) {
        if (fromIndex >= providers.size()) {
            showNoSource();
            return;
        }
        final int generation = ++resolveGeneration;
        final ContentProvider provider = providers.get(fromIndex);
        final int myIndex = fromIndex;
        runOnUiThread(() -> {
            if (!isCurrentResolve(generation)) return;
            providerIndex = myIndex;
            progress.setVisibility(View.VISIBLE);
            stateView.showMessage(getString(R.string.searching_source, provider.label(this)));
            sourceLabel.setText("");
        });

        provider.resolve(currentItem, new ContentProvider.Callback() {
            @Override
            public void onResolved(PlaybackSource source, String providerLabel) {
                runOnUiThread(() -> {
                    if (!isCurrentResolve(generation)) return;
                    startPlayback(source, providerLabel);
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (!isCurrentResolve(generation)) return;
                    resolveNextProvider(myIndex + 1);
                });
            }
        });
    }

    private void startPlayback(PlaybackSource source, String providerLabel) {
        activeProvider = providerLabel;
        progress.setVisibility(View.VISIBLE);
        stateView.showMessage(getString(R.string.preparing_watch));
        sourceLabel.setText(getString(R.string.source_prefix, providerLabel));
        nextButton.setVisibility(View.GONE);

        playbackController.prepare(currentItem.pageUrl, source.url, source.type, source.headers,
                new PlaybackController.Callback() {
                    @Override
                    public void onBuffering() {
                        progress.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onReady() {
                        progress.setVisibility(View.GONE);
                        stateView.hide();
                        playerView.setVisibility(View.VISIBLE);
                        immersive(true);
                        topBar.postDelayed(() -> {
                            if (playbackController != null && playbackController.isActive()) {
                                topBar.setVisibility(View.GONE);
                            }
                        }, 2500);
                    }

                    @Override
                    public void onEnded() {
                        handleEnded();
                    }

                    @Override
                    public void onError() {
                        failover();
                    }
                });
    }

    private void failover() {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            resolveNextProvider(providerIndex + 1);
        });
    }

    private void handleEnded() {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            playerView.setVisibility(View.GONE);
            sourceLabel.setText("");
            if (nextItem != null) {
                nextButton.setVisibility(View.VISIBLE);
                if (DeviceUtils.isTelevision(this)) nextButton.requestFocus();
            } else {
                stateView.showAction(getString(R.string.ended_message),
                        getString(R.string.play_again), v -> resolveNextProvider(0));
            }
        });
    }

    private void openNext() {
        if (nextItem == null) return;
        Intent intent = new Intent(this, WatchActivity.class);
        intent.putExtra("item", nextItem);
        intent.putExtra("title", nextItem.title);
        startActivity(intent);
        finish();
    }

    private void showNoSource() {
        progress.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        sourceLabel.setText("");
        stateView.showAction(getString(R.string.no_source_found), getString(R.string.retry),
                v -> resolveNextProvider(0));
    }

    private boolean isCurrentResolve(int generation) {
        return generation == resolveGeneration && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playbackController != null && playbackController.isActive()) {
            playbackController.resume();
            immersive(true);
        }
    }

    @Override
    protected void onPause() {
        if (playbackController != null && playbackController.isActive()) {
            historyStore.update(currentItem, playbackController.getCurrentPosition(),
                    playbackController.getDuration());
            playbackController.pause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && playbackController != null && playbackController.isActive()) {
            immersive(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (playbackController != null) {
            playbackController.release();
            playbackController = null;
        }
        super.onDestroy();
    }

    private void immersive(boolean enabled) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            @SuppressWarnings("deprecation")
            int flags = enabled
                    ? (View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                    : 0;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius, int stroke) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
