package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class FavoritesActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);

    private boolean television;
    private FavoritesStore store;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ContentStateView stateView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new FavoritesStore(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(television ? 24 : 10), dp(6), dp(television ? 24 : 10), dp(6));
        header.setBackgroundColor(SURFACE);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(television ? 68 : 56)));

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(television ? 16 : 13);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setFocusable(television);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(television ? 110 : 80), -1));

        TextView title = new TextView(this);
        title.setText(R.string.favorites);
        title.setTextColor(GOLD);
        title.setTextSize(television ? 25 : 19);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView hint = new TextView(this);
        hint.setText(R.string.favorite_remove_hint);
        hint.setTextColor(Color.rgb(174, 166, 187));
        hint.setTextSize(television ? 13 : 10);
        hint.setGravity(Gravity.CENTER);
        header.addView(hint, new LinearLayout.LayoutParams(dp(television ? 240 : 120), -1));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(television ? 16 : 5),
                dp(television ? 24 : 4), dp(24));
        grid.setItemAnimator(null);
        grid.setLayoutManager(new GridLayoutManager(this, television ? 5 : 3));
        adapter = new PosterAdapter(television, this::openDetails, this::confirmRemove);
        grid.setAdapter(adapter);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
        if (television) back.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFavorites();
    }

    private void refreshFavorites() {
        List<CatalogItem> items = store.getAll();
        adapter.submit(items);
        if (items.isEmpty()) {
            grid.setVisibility(View.GONE);
            stateView.showMessage(getString(R.string.favorites_empty));
            return;
        }

        stateView.hide();
        grid.setVisibility(View.VISIBLE);
        if (television) grid.postDelayed(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
            else grid.requestFocus();
        }, 180);
    }

    private void openDetails(CatalogItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private void confirmRemove(CatalogItem item) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.remove_favorite_title)
                .setMessage(item.title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (ignored, which) -> {
                    store.remove(item);
                    Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
                    refreshFavorites();
                })
                .create();
        dialog.setOnShowListener(ignored -> {
            if (television) dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
