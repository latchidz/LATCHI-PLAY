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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

public class DetailActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private CatalogItem item;
    private CatalogItem nextItem;
    private FavoritesStore favoritesStore;
    private boolean television;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        item = (CatalogItem) getIntent().getSerializableExtra("item");
        nextItem = (CatalogItem) getIntent().getSerializableExtra("next_item");
        if (item == null) { finish(); return; }
        favoritesStore = new FavoritesStore(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(television ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setPadding(dp(television ? 42 : 18), dp(television ? 28 : 18), dp(television ? 42 : 18), dp(28));
        root.setGravity(television ? Gravity.CENTER_VERTICAL : Gravity.TOP); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1)); setContentView(scroll);

        ImageView poster = new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(round(Color.rgb(25, 20, 34), dp(20), Color.rgb(78, 56, 104)));
        poster.setClipToOutline(true); Glide.with(this).load(item.imageUrl).centerCrop().into(poster);
        LinearLayout.LayoutParams posterParams = television
                ? new LinearLayout.LayoutParams(dp(300), dp(440))
                : new LinearLayout.LayoutParams(-1, dp(430));
        posterParams.setMargins(television ? dp(32) : 0, 0, 0, dp(18)); root.addView(poster, posterParams);

        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(television ? 28 : 4), dp(television ? 18 : 8), dp(television ? 28 : 4), dp(18));
        root.addView(info, television ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));

        TextView badge = text(item.type.equals("movie") ? "فيلم" : item.type.equals("series") ? "مسلسل" : "حلقة", television ? 16 : 13, GOLD, true);
        info.addView(badge, new LinearLayout.LayoutParams(-1, -2));
        TextView title = text(item.title, television ? 34 : 25, Color.WHITE, true); title.setGravity(Gravity.RIGHT); title.setPadding(0, dp(10), 0, dp(18));
        info.addView(title, new LinearLayout.LayoutParams(-1, -2));
        boolean seriesItem = "series".equals(item.type);
        Button watch = button(getString(seriesItem ? R.string.view_episodes : R.string.watch_now), PURPLE);
        watch.setOnClickListener(view -> {
            if (seriesItem) {
                Intent intent = new Intent(this, SeriesEpisodesActivity.class);
                intent.putExtra("item", item);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, WatchActivity.class);
                intent.putExtra("url", item.pageUrl);
                intent.putExtra("title", item.title);
                intent.putExtra("item", item);
                if (nextItem != null) intent.putExtra("next_item", nextItem);
                startActivity(intent);
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(television ? dp(280) : -1, dp(television ? 64 : 56)); bp.setMargins(0, dp(28), 0, dp(10)); info.addView(watch, bp);

        Button favorite = button(favoriteLabel(favoritesStore.isFavorite(item)), Color.rgb(34, 28, 45));
        favorite.setOnClickListener(view -> {
            boolean isFavorite = favoritesStore.toggle(item);
            favorite.setText(favoriteLabel(isFavorite));
        });
        info.addView(favorite, new LinearLayout.LayoutParams(television ? dp(280) : -1, dp(television ? 58 : 52)));

        Button back = button(getString(R.string.back), Color.rgb(24, 20, 32)); back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(television ? dp(180) : -1, dp(television ? 54 : 48)); backParams.setMargins(0, dp(10), 0, 0); info.addView(back, backParams);
        if (television) watch.requestFocus();
    }

    private String favoriteLabel(boolean favorite) {
        return getString(favorite ? R.string.favorite_saved : R.string.favorite_add);
    }

    private Button button(String label, int color) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(television ? 17 : 15);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); b.setFocusable(television); b.setFocusableInTouchMode(television); b.setBackground(round(color, dp(15), GOLD));
        if (television) b.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start()); return b;
    }
    private TextView text(String s, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(dp(1), stroke); return d; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
