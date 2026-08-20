package com.latchi.play;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.ui.PlayerView;

import java.util.Collections;
import java.util.List;

public class WatchActivity extends Activity {
    private static final String ALLOWED_HOST = "shooflive.net";
    private static final String CLEAN_PLAYER_JS =
            "(function(){document.cookie='sb_seen=1;path=/;max-age=31536000';" +
            "var st=document.createElement('style');st.textContent='" +
            "#headerNav,body>footer,.footer,#sbOverlay,.sbBox,.sbPopup,.singleInfo,.sec-line,.share-button-wrapper{display:none!important}" +
            "html,body,main,.secContainer,.containers{margin:0!important;padding:0!important;background:#000!important;width:100%!important;min-height:100%!important}" +
            ".getEmbed,.watch{margin:0!important;padding:0!important;width:100%!important;min-height:calc(100vh - 4px)!important;background:#000!important}" +
            ".getEmbed iframe,.watch iframe,.watch video{width:100%!important;min-height:80vh!important;border:0!important}';" +
            "document.documentElement.appendChild(st);" +
            "var p=document.getElementById('sbOverlay');if(p)p.remove();" +
            "document.querySelectorAll('a[target=\"_blank\"]').forEach(function(a){a.target='_self'});})();";

    private FrameLayout root;
    private LinearLayout chrome;
    private PlayerView playerView;
    private PlaybackController playbackController;
    private ServerResolver serverResolver;
    private WebView webView;
    private ProgressBar progress;
    private ContentStateView stateView;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String currentUrl;
    private List<PlaybackSource> resolvedSources = Collections.emptyList();
    private int sourceIndex;
    private int resolverGeneration;
    private boolean nativePlayback;
    private boolean nativeReady;
    private boolean webFallbackActive;
    private boolean mainFrameFailed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        buildUi();
        configureWebView();
        serverResolver = new ServerResolver();

        currentUrl = getIntent().getStringExtra("url");
        if (!isAllowedUrl(currentUrl)) {
            finish();
            return;
        }

        String suppliedUrl = getIntent().getStringExtra("direct_url");
        String suppliedType = getIntent().getStringExtra("direct_type");
        if (isDirectMediaUrl(suppliedUrl)) {
            resolvedSources = Collections.singletonList(new PlaybackSource(
                    suppliedUrl, suppliedType, Collections.emptyMap(),
                    Collections.singletonMap("origin", "intent")));
            sourceIndex = 0;
            startNativePlayback(resolvedSources.get(0));
        } else {
            resolveAndPreparePlayback();
        }
    }

    private void buildUi() {
        boolean television = DeviceUtils.isTelevision(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        root.addView(chrome, match());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(4), dp(12), dp(4));
        bar.setBackgroundColor(Color.rgb(13, 10, 18));
        chrome.addView(bar, new LinearLayout.LayoutParams(-1, dp(50)));

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setFocusable(television);
        back.setOnClickListener(view -> onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(dp(90), -1));

        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra("title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView brand = new TextView(this);
        brand.setText(R.string.app_name);
        brand.setTextColor(Color.rgb(246, 198, 75));
        brand.setTextSize(14);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        bar.addView(brand, new LinearLayout.LayoutParams(dp(130), -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(124, 58, 237)));
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

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setVisibility(View.GONE);
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        stateView.showMessage(getString(R.string.preparing_watch));

        if (television) back.requestFocus();
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUserAgentString(settings.getUserAgentString() + " LatchiPlay/" + BuildConfig.VERSION_NAME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        cookies.setCookie("https://shooflive.net/", "sb_seen=1; Max-Age=31536000; Path=/; Secure");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mainFrameFailed = false;
                progress.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                stateView.showMessage(getString(R.string.preparing_watch));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mainFrameFailed || isFinishing()) return;
                progress.setVisibility(View.GONE);
                stateView.hide();
                webView.setVisibility(View.VISIBLE);
                view.evaluateJavascript(CLEAN_PLAYER_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showWatchError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) showWatchError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && !isAllowedHost(uri.getHost())) {
                    Toast.makeText(WatchActivity.this, R.string.external_window_blocked, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                chrome.setVisibility(View.GONE);
                root.addView(view, match());
                immersive(true);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });
    }

    private void resolveAndPreparePlayback() {
        if (!DeviceUtils.hasInternetConnection(this)) {
            showWatchError();
            return;
        }

        int generation = ++resolverGeneration;
        nativePlayback = false;
        webFallbackActive = false;
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateView.showMessage(getString(R.string.preparing_watch));

        serverResolver.resolve(currentUrl, new ServerResolver.Callback() {
            @Override
            public void onResolved(ServerResolver.Result result) {
                runOnUiThread(() -> {
                    if (generation != resolverGeneration || isFinishing() || isDestroyed()) return;
                    resolvedSources = result.sources;
                    sourceIndex = 0;
                    if (resolvedSources.isEmpty()) loadWatchPage();
                    else startNativePlayback(resolvedSources.get(0));
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (generation != resolverGeneration || isFinishing() || isDestroyed()) return;
                    loadWatchPage();
                });
            }
        });
    }

    private void tryNextSourceOrFallback() {
        sourceIndex++;
        if (sourceIndex < resolvedSources.size()) {
            stateView.showMessage(getString(R.string.trying_next_server));
            startNativePlayback(resolvedSources.get(sourceIndex));
            return;
        }
        loadWatchPage();
    }

    private void startNativePlayback(PlaybackSource source) {
        nativePlayback = true;
        nativeReady = false;
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        stateView.showMessage(getString(R.string.preparing_watch));

        playbackController.prepare(currentUrl, source.url, source.type, source.headers,
                new PlaybackController.Callback() {
                    @Override
                    public void onBuffering() {
                        progress.setVisibility(View.VISIBLE);
                        if (!nativeReady) stateView.showMessage(getString(R.string.preparing_watch));
                    }

                    @Override
                    public void onReady() {
                        nativeReady = true;
                        progress.setVisibility(View.GONE);
                        stateView.hide();
                        playerView.setVisibility(View.VISIBLE);
                        immersive(true);
                        if (DeviceUtils.isTelevision(WatchActivity.this)) playerView.requestFocus();
                    }

                    @Override
                    public void onEnded() {
                        progress.setVisibility(View.GONE);
                        playerView.showController();
                    }

                    @Override
                    public void onError() {
                        nativeReady = false;
                        progress.setVisibility(View.GONE);
                        playerView.setVisibility(View.GONE);
                        tryNextSourceOrFallback();
                    }
                });
    }

    private void loadWatchPage() {
        nativePlayback = false;
        webFallbackActive = true;
        immersive(false);
        playerView.setVisibility(View.GONE);
        if (playbackController.isActive()) playbackController.release();
        if (!DeviceUtils.hasInternetConnection(this)) {
            showWatchError();
            return;
        }
        mainFrameFailed = false;
        progress.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        stateView.showMessage(getString(R.string.preparing_watch));
        webView.loadUrl(currentUrl);
    }

    private void showWatchError() {
        mainFrameFailed = true;
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        String message = DeviceUtils.hasInternetConnection(this)
                ? getString(R.string.load_watch_failed)
                : getString(R.string.no_internet);
        stateView.showAction(message, getString(R.string.retry), view -> {
            if (webFallbackActive) loadWatchPage();
            else resolveAndPreparePlayback();
        });
    }

    private boolean isDirectMediaUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd");
    }

    private boolean isAllowedUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && isAllowedHost(uri.getHost());
    }

    private boolean isAllowedHost(String host) {
        return host != null && (host.equalsIgnoreCase(ALLOWED_HOST) ||
                host.toLowerCase().endsWith("." + ALLOWED_HOST));
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        chrome.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        immersive(false);
    }

    private void immersive(boolean hide) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (hide) controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(hide ? 5894 : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) hideCustomView();
        else if (nativePlayback) finish();
        else if (webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nativePlayback && playbackController != null) playbackController.resume();
        else if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (nativePlayback && playbackController != null) playbackController.pause();
        else if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        resolverGeneration++;
        immersive(false);
        if (serverResolver != null) serverResolver.destroy();
        if (playbackController != null) playbackController.release();
        if (customView != null) hideCustomView();
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
