package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;



public class MainActivity extends Activity {
    private static final String HOME_URL = "https://shooflive.net/";
    private static final String PREFS = "latchi_preferences";
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int BLACK = Color.rgb(7, 6, 11);
    private static final int SURFACE = Color.rgb(18, 16, 25);
    private static final int MUTED = Color.rgb(170, 164, 180);

    private FrameLayout root;
    private LinearLayout appChrome;
    private WebView webView;
    private ProgressBar pageProgress;
    private LinearLayout errorPanel;
    private TextView statusText;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private long lastBackPress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        configureWebView();

        String initialUrl = HOME_URL;
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            if (webView.getUrl() != null) return;
        } else {
            String incoming = getIntent() != null && getIntent().getDataString() != null
                    ? getIntent().getDataString() : null;
            if (incoming != null && incoming.startsWith("https://shooflive.net/")) {
                initialUrl = incoming;
            } else {
                initialUrl = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString("last_url", HOME_URL);
            }
        }
        load(initialUrl);
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        setContentView(root);

        appChrome = new LinearLayout(this);
        appChrome.setOrientation(LinearLayout.VERTICAL);
        appChrome.setBackgroundColor(BLACK);
        root.addView(appChrome, match());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(10), dp(8));
        header.setBackgroundColor(SURFACE);
        appChrome.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView brand = new TextView(this);
        brand.setText("LATCHI  PLAY");
        brand.setTextColor(GOLD);
        brand.setTextSize(19);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));

        statusText = new TextView(this);
        statusText.setText("●  متصل");
        statusText.setTextColor(Color.rgb(74, 222, 128));
        statusText.setTextSize(11);
        statusText.setGravity(Gravity.CENTER);
        header.addView(statusText, new LinearLayout.LayoutParams(dp(70), -1));

        Button search = smallButton("بحث");
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(66), dp(40)));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        pageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        appChrome.addView(pageProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        appChrome.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        webView.setBackgroundColor(BLACK);
        content.addView(webView, match());

        errorPanel = buildErrorPanel();
        errorPanel.setVisibility(View.GONE);
        content.addView(errorPanel, match());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(5), dp(5), dp(5), dp(5));
        navigation.setBackgroundColor(SURFACE);
        appChrome.addView(navigation, new LinearLayout.LayoutParams(-1, dp(64)));

        addNav(navigation, "الرئيسية", HOME_URL);
        addNav(navigation, "الأفلام", "https://shooflive.net/foreign-movies/");
        addNav(navigation, "المسلسلات", "https://shooflive.net/arabic-series/");
        addNav(navigation, "الأخبار", "https://shooflive.net/news/");
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " LatchiPlay/2.0 AndroidApp");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new BrowserClient());
        webView.setWebChromeClient(new PlayerChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                Toast.makeText(this, "التنزيل غير متاح داخل التطبيق", Toast.LENGTH_SHORT).show());
    }

    private class BrowserClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageProgress.setVisibility(View.VISIBLE);
            errorPanel.setVisibility(View.GONE);
            statusText.setText("●  تحميل");
            statusText.setTextColor(GOLD);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageProgress.setVisibility(View.GONE);
            statusText.setText("●  متصل");
            statusText.setTextColor(Color.rgb(74, 222, 128));
            if (url != null && url.startsWith("https://")) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_url", url).apply();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(Uri.parse(url));
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) showError();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 500) showError();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Toast.makeText(MainActivity.this, "تمت إعادة تشغيل صفحة المشاهدة", Toast.LENGTH_SHORT).show();
            root.removeView(view);
            recreate();
            return true;
        }
    }

    private class PlayerChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            pageProgress.setProgress(progress);
            pageProgress.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            appChrome.setVisibility(View.GONE);
            root.addView(view, match());
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            enterImmersiveMode();
        }

        @Override
        public void onHideCustomView() {
            exitFullscreenPlayer();
        }
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) {
            return false;
        }
        if (scheme.equals("intent")) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح الرابط", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private LinearLayout buildErrorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setBackgroundColor(BLACK);

        TextView title = new TextView(this);
        title.setText("تعذّر تحميل المحتوى");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = new TextView(this);
        message.setText("تأكد من اتصال الإنترنت ثم حاول مرة أخرى");
        message.setTextColor(MUTED);
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.setMargins(0, dp(10), 0, dp(24));
        panel.addView(message, messageParams);

        Button retry = smallButton("إعادة المحاولة");
        retry.setTextColor(BLACK);
        retry.setBackground(rounded(GOLD, dp(14)));
        retry.setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            if (isOnline()) webView.reload(); else showError();
        });
        panel.addView(retry, new LinearLayout.LayoutParams(dp(180), dp(48)));
        return panel;
    }

    private void addNav(LinearLayout parent, String title, String url) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(rounded(Color.TRANSPARENT, dp(12)));
        button.setOnClickListener(v -> load(url));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, params);
    }

    private Button smallButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(Color.rgb(34, 29, 46), dp(12)));
        return button;
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("اسم الفيلم أو المسلسل");
        input.setSingleLine(true);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.DKGRAY);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("البحث")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("بحث", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String query = input.getText().toString().trim();
            if (query.isEmpty()) return;
            String encoded = Uri.encode(query);
            load(HOME_URL + "?s=" + encoded);
            dialog.dismiss();
        }));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void load(String url) {
        if (!isOnline()) {
            showError();
            return;
        }
        errorPanel.setVisibility(View.GONE);
        webView.loadUrl(url == null || !url.startsWith("https://") ? HOME_URL : url);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showError() {
        pageProgress.setVisibility(View.GONE);
        statusText.setText("●  غير متصل");
        statusText.setTextColor(Color.rgb(248, 113, 113));
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void exitFullscreenPlayer() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        appChrome.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreenPlayer();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else if (System.currentTimeMillis() - lastBackPress < 1800) {
            super.onBackPressed();
        } else {
            lastBackPress = System.currentTimeMillis();
            Toast.makeText(this, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
