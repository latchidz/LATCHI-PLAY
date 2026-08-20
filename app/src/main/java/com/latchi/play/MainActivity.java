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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://shooflive.net/";
    private static final String PREFS = "latchi_preferences";
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int BLACK = Color.rgb(7, 6, 11);
    private static final int SURFACE = Color.rgb(18, 16, 25);
    private static final int SURFACE_RAISED = Color.rgb(27, 24, 36);
    private static final int MUTED = Color.rgb(170, 164, 180);

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "www.googletagmanager.com", "googletagmanager.com",
            "www.google-analytics.com", "google-analytics.com",
            "pagead2.googlesyndication.com", "googleads.g.doubleclick.net",
            "adservice.google.com", "static.cloudflareinsights.com",
            "connect.facebook.net", "analytics.tiktok.com"
    ));

    private static final String CLEAN_PAGE_JS =
            "(function(){" +
            "document.cookie='sb_seen=1;path=/;max-age=31536000';" +
            "document.title=(document.title||'').replace(/شوف لايف/g,'LATCHI PLAY');" +
            "function clean(){" +
            "var selectors=['#sbOverlay','.sbBox','.sbPopup','#headerNav','body>footer','.footer','#search.con_search','.share-button-wrapper','.share-popup-overlay','script[src*=\"googletagmanager\"]','iframe[src*=\"doubleclick\"]','iframe[src*=\"googlesyndication\"]'];" +
            "selectors.forEach(function(s){document.querySelectorAll(s).forEach(function(e){e.remove();});});" +
            "document.querySelectorAll('a[href*=\"t.me/SHOOFVIP\"]').forEach(function(e){e.remove();});" +
            "document.querySelectorAll('a[target=\"_blank\"]').forEach(function(e){e.setAttribute('target','_self');});" +
            "}" +
            "clean();" +
            "if(!document.getElementById('latchi-theme')){" +
            "var style=document.createElement('style');style.id='latchi-theme';" +
            "style.textContent=`" +
            ":root{--lp-bg:#08070d;--lp-surface:#15111d;--lp-raised:#20192c;--lp-purple:#8b5cf6;--lp-gold:#f6c64b;--lp-text:#fffdf8;--lp-muted:#aaa4b4}" +
            "html,body{background:var(--lp-bg)!important;color:var(--lp-text)!important;min-height:100%!important}" +
            "body{margin:0!important;padding:0!important;font-family:Arial,sans-serif!important}" +
            "#headerNav,body>footer,.footer,#sbOverlay,.sbBox,.sbPopup,#search.con_search,.share-button-wrapper,.share-popup-overlay{display:none!important}" +
            "main{margin:0!important;padding:0!important;background:var(--lp-bg)!important;min-height:100vh!important}" +
            ".sec-line,.secContainer,.secContainer.bg{background:var(--lp-bg)!important;margin:0!important;padding:12px 0 22px!important}" +
            ".containers.container-fluid,.container-fluid.containers{width:100%!important;max-width:none!important;padding:0 10px!important;margin:0!important}" +
            "#load-post,#load-post-movies,#load-post-episodes{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:10px!important;width:100%!important}" +
            "#load-post:before,#load-post:after,#load-post-movies:before,#load-post-movies:after,#load-post-episodes:before,#load-post-episodes:after{display:none!important}" +
            "article.post,article.postEp{display:block!important;width:100%!important;margin:0!important;padding:0!important;float:none!important}" +
            "article.post>div,article.postEp>div{display:block!important;width:100%!important;margin:0!important;padding:0!important;float:none!important}" +
            ".block-post,.poster{position:relative!important;border-radius:16px!important;overflow:hidden!important;background:var(--lp-surface)!important;border:1px solid rgba(139,92,246,.18)!important;box-shadow:0 8px 26px rgba(0,0,0,.32)!important;margin:0!important;transition:transform .18s ease!important}" +
            ".block-post:active,.poster:active{transform:scale(.98)!important}" +
            ".posterThumb,.poster.img-cnt{width:100%!important;aspect-ratio:2/3!important;height:auto!important;background:#16121e!important;overflow:hidden!important}" +
            ".posterThumb img,.poster img,.imgBg,.imgSer{width:100%!important;height:100%!important;object-fit:cover!important;display:block!important}" +
            ".block-post .title,.poster .title{position:absolute!important;left:0!important;right:0!important;bottom:0!important;margin:0!important;padding:42px 10px 11px!important;min-height:68px!important;background:linear-gradient(transparent,rgba(6,5,10,.96))!important;color:#fff!important;font-size:14px!important;font-weight:700!important;line-height:1.45!important;text-align:right!important}" +
            ".ribbon,.episodeNum{background:linear-gradient(135deg,var(--lp-purple),#6d28d9)!important;color:#fff!important;border:0!important;border-radius:0 0 0 10px!important;box-shadow:none!important}" +
            ".pagination{display:flex!important;justify-content:center!important;gap:7px!important;margin:22px 0!important}" +
            ".pagination a,.pagination span{background:var(--lp-raised)!important;color:#fff!important;border:1px solid rgba(139,92,246,.25)!important;border-radius:10px!important;padding:9px 13px!important}" +
            ".pagination .current{background:linear-gradient(135deg,var(--lp-purple),#6d28d9)!important;color:#fff!important}" +
            ".getEmbed,.watch{border-radius:16px!important;overflow:hidden!important;background:#000!important;margin:0 0 14px!important;box-shadow:0 12px 35px rgba(0,0,0,.5)!important}" +
            ".singleInfo{padding:12px 10px 24px!important;background:var(--lp-bg)!important}" +
            ".singleSeries{display:flex!important;gap:14px!important;align-items:flex-start!important;background:linear-gradient(145deg,var(--lp-raised),var(--lp-surface))!important;border:1px solid rgba(139,92,246,.22)!important;border-radius:18px!important;padding:14px!important;box-shadow:0 12px 30px rgba(0,0,0,.28)!important}" +
            ".singleSeries .cover{width:34%!important;min-width:105px!important;border-radius:13px!important;overflow:hidden!important;margin:0!important}" +
            ".singleSeries .cover img{width:100%!important;height:auto!important;display:block!important}" +
            ".singleSeries .info{flex:1!important;color:#fff!important;margin:0!important;padding:0!important}" +
            ".singleSeries h1,.singleSeries h2,.singleSeries .title{color:var(--lp-gold)!important;font-weight:800!important;line-height:1.4!important}" +
            ".story{background:var(--lp-surface)!important;color:#ddd8e5!important;border-right:3px solid var(--lp-purple)!important;border-radius:12px!important;padding:14px!important;margin-top:13px!important;line-height:1.9!important}" +
            ".slv2{background:var(--lp-bg)!important;min-height:100vh!important;padding:22px 12px!important}" +
            ".slv2-top{display:block!important;text-align:right!important;padding:0!important;margin-bottom:15px!important}" +
            ".slv2-title{color:#fff!important;font-size:24px!important}.slv2-title em,.slv2-title strong{color:var(--lp-gold)!important}" +
            ".slv2-count{color:var(--lp-muted)!important}" +
            ".slv2-form{border:1px solid rgba(139,92,246,.55)!important;border-radius:14px!important;overflow:hidden!important;background:var(--lp-surface)!important}" +
            ".slv2-form button,.slv2-form input[type=submit]{background:linear-gradient(135deg,var(--lp-purple),#6d28d9)!important}" +
            ".slv2-grid{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:12px!important}" +
            ".slv2-card{width:100%!important;margin:0!important;color:#fff!important;text-decoration:none!important}" +
            ".slv2-poster{width:100%!important;aspect-ratio:2/3!important;border-radius:16px!important;overflow:hidden!important;border:1px solid rgba(139,92,246,.22)!important;background:var(--lp-surface)!important}" +
            ".slv2-poster img{width:100%!important;height:100%!important;object-fit:cover!important}" +
            ".slv2-badge{background:var(--lp-purple)!important;color:#fff!important}.slv2-name{color:#fff!important;text-align:right!important;font-weight:700!important;padding:8px 3px!important}" +
            ".block-news{background:var(--lp-surface)!important;border-radius:15px!important;overflow:hidden!important;border:1px solid rgba(139,92,246,.18)!important}" +
            "@media(min-width:700px){#load-post,#load-post-movies,#load-post-episodes,.slv2-grid{grid-template-columns:repeat(4,minmax(0,1fr))!important}}" +
            "`;document.documentElement.appendChild(style);}" +
            "new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});" +
            "})();";

    private FrameLayout root;
    private LinearLayout appChrome;
    private FrameLayout contentFrame;
    private WebView webView;
    private View nativeHome;
    private ProgressBar pageProgress;
    private LinearLayout errorPanel;
    private TextView statusText;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private UpdateManager updateManager;
    private long lastBackPress;
    private boolean showingNativeHome = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        configureWebView();
        updateManager = new UpdateManager(this);
        updateManager.checkAutomatically();

        String incoming = getIntent() != null ? getIntent().getDataString() : null;
        if (incoming != null && isShoofHost(Uri.parse(incoming).getHost())) {
            load(incoming);
        } else {
            showNativeHome();
        }
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

        ImageView brandIcon = new ImageView(this);
        brandIcon.setImageResource(R.mipmap.ic_launcher);
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        brandIcon.setOnClickListener(v -> showNativeHome());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMargins(0, 0, dp(9), 0);
        header.addView(brandIcon, iconParams);

        TextView brand = new TextView(this);
        brand.setText("LATCHI PLAY");
        brand.setTextColor(GOLD);
        brand.setTextSize(18);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        brand.setOnClickListener(v -> showNativeHome());
        brand.setOnLongClickListener(v -> {
            if (updateManager != null) updateManager.checkManually();
            return true;
        });
        header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));

        statusText = new TextView(this);
        statusText.setText("●  جاهز");
        statusText.setTextColor(Color.rgb(74, 222, 128));
        statusText.setTextSize(11);
        statusText.setGravity(Gravity.CENTER);
        statusText.setOnClickListener(v -> {
            if (updateManager != null) updateManager.checkManually();
        });
        header.addView(statusText, new LinearLayout.LayoutParams(dp(64), -1));

        Button search = smallButton("بحث");
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(66), dp(40)));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        pageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        pageProgress.setVisibility(View.GONE);
        appChrome.addView(pageProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        contentFrame = new FrameLayout(this);
        appChrome.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        webView.setBackgroundColor(BLACK);
        webView.setVisibility(View.GONE);
        contentFrame.addView(webView, match());

        nativeHome = buildNativeHome();
        contentFrame.addView(nativeHome, match());

        errorPanel = buildErrorPanel();
        errorPanel.setVisibility(View.GONE);
        contentFrame.addView(errorPanel, match());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(5), dp(5), dp(5), dp(5));
        navigation.setBackgroundColor(SURFACE);
        appChrome.addView(navigation, new LinearLayout.LayoutParams(-1, dp(64)));

        addNav(navigation, "الرئيسية", v -> showNativeHome());
        addNav(navigation, "الأفلام", v -> showCategoryDialog(true));
        addNav(navigation, "المسلسلات", v -> showCategoryDialog(false));
        addNav(navigation, "الأخبار", v -> load("https://shooflive.net/news/"));
    }

    private View buildNativeHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BLACK);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        TextView hello = new TextView(this);
        hello.setText("ماذا تريد أن تشاهد؟");
        hello.setTextColor(Color.WHITE);
        hello.setTextSize(27);
        hello.setGravity(Gravity.RIGHT);
        hello.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        page.addView(hello, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("اختر القسم ثم الفئة المناسبة");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, dp(5), 0, dp(22));
        page.addView(subtitle, subtitleParams);

        page.addView(sectionHeader("الأفلام", "أحدث الأفلام العربية والعالمية"));
        addCategoryGrid(page, new String[][]{
                {"أفلام أجنبية", "https://shooflive.net/foreign-movies/"},
                {"أفلام عربية", "https://shooflive.net/arabic-movies/"},
                {"أفلام تركية", "https://shooflive.net/turkish-movies/"},
                {"أفلام آسيوية", "https://shooflive.net/asian-movies/"},
                {"أفلام هندية", "https://shooflive.net/indian-movies/"},
                {"أفلام أنيميشن", "https://shooflive.net/animation-movies/"}
        });

        LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(-1, dp(22));
        page.addView(new View(this), spacer);

        page.addView(sectionHeader("المسلسلات", "حلقات ومسلسلات من مختلف البلدان"));
        addCategoryGrid(page, new String[][]{
                {"مسلسلات عربية", "https://shooflive.net/arabic-series/"},
                {"مسلسلات تركية", "https://shooflive.net/turkish-series/"},
                {"مسلسلات أجنبية", "https://shooflive.net/foreign-series/"},
                {"مسلسلات آسيوية", "https://shooflive.net/asian-series/"},
                {"مسلسلات مدبلجة", "https://shooflive.net/dubbed-series/"},
                {"مسلسلات قصيرة", "https://shooflive.net/short-series/"},
                {"رمضان 2026", "https://shooflive.net/ramadan-series-2026/"}
        });
        return scroll;
    }

    private View sectionHeader(String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(15));
        card.setBackground(gradientCard());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(GOLD);
        titleView.setTextSize(21);
        titleView.setGravity(Gravity.RIGHT);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView descriptionView = new TextView(this);
        descriptionView.setText(description);
        descriptionView.setTextColor(MUTED);
        descriptionView.setTextSize(13);
        descriptionView.setGravity(Gravity.RIGHT);
        card.addView(descriptionView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private void addCategoryGrid(LinearLayout parent, String[][] items) {
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(58));
            rowParams.setMargins(0, 0, 0, dp(9));
            parent.addView(row, rowParams);

            addCategoryButton(row, items[i][0], items[i][1]);
            if (i + 1 < items.length) {
                addCategoryButton(row, items[i + 1][0], items[i + 1][1]);
            } else {
                View empty = new View(this);
                row.addView(empty, new LinearLayout.LayoutParams(0, -1, 1));
            }
        }
    }

    private void addCategoryButton(LinearLayout row, String title, String url) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(rounded(SURFACE_RAISED, dp(15)));
        button.setOnClickListener(v -> load(url));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(button, params);
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
        settings.setUserAgentString(settings.getUserAgentString() + " LatchiPlay/2.1 AndroidApp");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        cookies.setCookie(HOME_URL, "sb_seen=1; Max-Age=31536000; Path=/; Secure; SameSite=Lax");

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
            view.evaluateJavascript(CLEAN_PAGE_JS, null);
            if (url != null && isShoofHost(Uri.parse(url).getHost())) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_url", url).apply();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (request.isForMainFrame() && isExternalWebUri(uri)) {
                Toast.makeText(MainActivity.this, "تم منع نافذة خارجية", Toast.LENGTH_SHORT).show();
                return true;
            }
            return handleNonWebNavigation(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isExternalWebUri(uri)) return true;
            return handleNonWebNavigation(uri);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();
            if (isBlockedHost(host)) return emptyResponse();
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            String host = Uri.parse(url).getHost();
            if (isBlockedHost(host)) return emptyResponse();
            return super.shouldInterceptRequest(view, url);
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
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
            Toast.makeText(MainActivity.this, "تم منع نافذة منبثقة", Toast.LENGTH_SHORT).show();
            return false;
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

    private void showNativeHome() {
        showingNativeHome = true;
        pageProgress.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        nativeHome.setVisibility(View.VISIBLE);
        statusText.setText("●  جاهز");
        statusText.setTextColor(Color.rgb(74, 222, 128));
    }

    private void showCategoryDialog(boolean movies) {
        String[] labels = movies
                ? new String[]{"أجنبية", "عربية", "تركية", "آسيوية", "هندية", "أنيميشن"}
                : new String[]{"عربية", "تركية", "أجنبية", "آسيوية", "مدبلجة", "قصيرة", "رمضان 2026"};
        String[] urls = movies
                ? new String[]{"foreign-movies", "arabic-movies", "turkish-movies", "asian-movies", "indian-movies", "animation-movies"}
                : new String[]{"arabic-series", "turkish-series", "foreign-series", "asian-series", "dubbed-series", "short-series", "ramadan-series-2026"};
        new AlertDialog.Builder(this)
                .setTitle(movies ? "فئات الأفلام" : "فئات المسلسلات")
                .setItems(labels, (dialog, which) -> load(HOME_URL + urls[which] + "/"))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private boolean handleNonWebNavigation(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) return false;
        if (scheme.equals("intent")) return true;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح الرابط", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean isExternalWebUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return !isShoofHost(uri.getHost());
    }

    private boolean isShoofHost(String host) {
        return host != null && (host.equalsIgnoreCase("shooflive.net") || host.endsWith(".shooflive.net"));
    }

    private boolean isBlockedHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase();
        if (BLOCKED_HOSTS.contains(lower)) return true;
        return lower.endsWith(".doubleclick.net") || lower.endsWith(".googlesyndication.com") ||
                lower.endsWith(".google-analytics.com");
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
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

    private void addNav(LinearLayout parent, String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackground(rounded(Color.TRANSPARENT, dp(12)));
        button.setOnClickListener(listener);
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
            load(HOME_URL + "?s=" + Uri.encode(query));
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
        showingNativeHome = false;
        nativeHome.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
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
        showingNativeHome = false;
        nativeHome.setVisibility(View.GONE);
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
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
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
        } else if (!showingNativeHome && webView.canGoBack()) {
            webView.goBack();
        } else if (!showingNativeHome) {
            showNativeHome();
        } else if (System.currentTimeMillis() - lastBackPress < 1800) {
            super.onBackPressed();
        } else {
            lastBackPress = System.currentTimeMillis();
            Toast.makeText(this, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) updateManager.destroy();
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

    private GradientDrawable gradientCard() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(35, 25, 50), Color.rgb(22, 18, 31)});
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), Color.rgb(82, 54, 112));
        return drawable;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
