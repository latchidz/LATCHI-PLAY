package com.latchi.play;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean tv = DeviceUtils.isTelevision(this);
        setRequestedOrientation(tv ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(Color.rgb(7, 5, 12));
        getWindow().setNavigationBarColor(Color.rgb(7, 5, 12));

        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        ImageView background = new ImageView(this); background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImageResource(R.drawable.splash_bg_v5); root.addView(background, match());
        View shade = new View(this); shade.setBackgroundColor(Color.argb(55,0,0,0)); root.addView(shade, match());

        ImageView glow = new ImageView(this); glow.setImageResource(R.drawable.splash_glow_v5); glow.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(tv?330:245),dp(tv?330:245),Gravity.CENTER); gp.bottomMargin=dp(tv?35:75); root.addView(glow,gp);
        ImageView icon = new ImageView(this); icon.setImageResource(R.mipmap.ic_launcher); icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(tv?170:132),dp(tv?170:132),Gravity.CENTER); ip.bottomMargin=dp(tv?35:75); root.addView(icon,ip);
        TextView title = new TextView(this); title.setText("LATCHI PLAY"); title.setTextColor(Color.rgb(255,244,205)); title.setTextSize(tv?35:27); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); title.setLetterSpacing(.12f);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1,dp(tv?70:60),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM); tp.bottomMargin=dp(tv?48:95); tp.leftMargin=dp(25);tp.rightMargin=dp(25);root.addView(title,tp);

        background.setScaleX(1.12f);background.setScaleY(1.12f);background.setAlpha(0f);
        glow.setAlpha(0f);glow.setScaleX(.55f);glow.setScaleY(.55f);icon.setAlpha(0f);icon.setScaleX(.65f);icon.setScaleY(.65f);title.setAlpha(0f);title.setTranslationY(dp(30));
        DecelerateInterpolator ease=new DecelerateInterpolator();
        background.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1500).setInterpolator(ease).start();
        glow.animate().alpha(.82f).scaleX(1f).scaleY(1f).rotation(4f).setStartDelay(350).setDuration(1200).setInterpolator(ease).start();
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(650).setDuration(850).setInterpolator(ease).start();
        title.animate().alpha(1f).translationY(0).setStartDelay(900).setDuration(750).setInterpolator(ease).withEndAction(() ->
                root.postDelayed(() -> { startActivity(new Intent(this,MainActivity.class)); overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out); finish(); },850)).start();
    }
    private FrameLayout.LayoutParams match(){return new FrameLayout.LayoutParams(-1,-1);} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
