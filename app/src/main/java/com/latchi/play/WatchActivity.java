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
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class WatchActivity extends Activity {
    private static final String CLEAN_PLAYER_JS = "(function(){document.cookie='sb_seen=1;path=/;max-age=31536000';" +
            "var st=document.createElement('style');st.textContent='#headerNav,body>footer,.footer,#sbOverlay,.sbBox,.sbPopup,.singleInfo,.sec-line,.share-button-wrapper{display:none!important}html,body,main,.secContainer,.containers{margin:0!important;padding:0!important;background:#000!important;width:100%!important;min-height:100%!important}.getEmbed,.watch{margin:0!important;padding:0!important;width:100%!important;min-height:calc(100vh - 4px)!important;background:#000!important}.getEmbed iframe,.watch iframe,.watch video{width:100%!important;min-height:80vh!important;border:0!important}';document.documentElement.appendChild(st);var p=document.getElementById('sbOverlay');if(p)p.remove();document.querySelectorAll('a[target=\"_blank\"]').forEach(function(a){a.target='_self'});})();";

    private FrameLayout root;
    private LinearLayout chrome;
    private WebView webView;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        chrome = new LinearLayout(this); chrome.setOrientation(LinearLayout.VERTICAL); root.addView(chrome, match());
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(10), dp(4), dp(12), dp(4)); bar.setBackgroundColor(Color.rgb(13, 10, 18));
        chrome.addView(bar, new LinearLayout.LayoutParams(-1, dp(50)));
        Button back = new Button(this); back.setText("رجوع"); back.setAllCaps(false); back.setTextColor(Color.WHITE); back.setBackgroundColor(Color.TRANSPARENT); back.setOnClickListener(v -> onBackPressed()); bar.addView(back, new LinearLayout.LayoutParams(dp(90), -1));
        TextView title = new TextView(this); title.setText(getIntent().getStringExtra("title")); title.setTextColor(Color.WHITE); title.setTextSize(16); title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT); title.setMaxLines(1); bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView brand = new TextView(this); brand.setText("LATCHI PLAY"); brand.setTextColor(Color.rgb(246,198,75)); brand.setTextSize(14); brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); brand.setGravity(Gravity.CENTER); bar.addView(brand, new LinearLayout.LayoutParams(dp(130), -1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(124,58,237))); chrome.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        webView = new WebView(this); webView.setBackgroundColor(Color.BLACK); chrome.addView(webView, new LinearLayout.LayoutParams(-1,0,1)); configure();
        String url = getIntent().getStringExtra("url"); if (url == null || !url.startsWith("https://shooflive.net/")) { finish(); return; } webView.loadUrl(url);
        if (DeviceUtils.isTelevision(this)) back.requestFocus();
    }

    @SuppressWarnings("SetJavaScriptEnabled") private void configure() {
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setUserAgentString(s.getUserAgentString()+" LatchiPlay/3.0");
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true); CookieManager.getInstance().setCookie("https://shooflive.net/","sb_seen=1; Max-Age=31536000; Path=/; Secure");
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u, Bitmap f){progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView v,String u){progress.setVisibility(View.GONE);v.evaluateJavascript(CLEAN_PLAYER_JS,null);}
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){Uri u=r.getUrl(); if(r.isForMainFrame() && u.getHost()!=null && !u.getHost().endsWith("shooflive.net")){Toast.makeText(WatchActivity.this,"تم منع نافذة خارجية",Toast.LENGTH_SHORT).show();return true;}return false;}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView v,boolean d,boolean g,android.os.Message m){return false;}
            @Override public void onShowCustomView(View v,CustomViewCallback cb){if(customView!=null){cb.onCustomViewHidden();return;}customView=v;customCallback=cb;chrome.setVisibility(View.GONE);root.addView(v,match());immersive(true);}
            @Override public void onHideCustomView(){hideCustom();}
        });
    }
    private void hideCustom(){if(customView==null)return;root.removeView(customView);customView=null;chrome.setVisibility(View.VISIBLE);if(customCallback!=null)customCallback.onCustomViewHidden();customCallback=null;immersive(false);}
    private void immersive(boolean hide){if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){if(hide)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(hide?5894:View.SYSTEM_UI_FLAG_VISIBLE);}
    @Override public void onBackPressed(){if(customView!=null)hideCustom();else if(webView.canGoBack())webView.goBack();else finish();}
    @Override protected void onDestroy(){if(webView!=null){webView.stopLoading();webView.destroy();}super.onDestroy();}
    private FrameLayout.LayoutParams match(){return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
