package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends Activity {
    private static final String BASE = "https://shooflive.net/";
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private boolean television;
    private CatalogClient client;
    private PosterAdapter adapter;
    private RecyclerView grid;
    private ProgressBar progress;
    private TextView screenTitle;
    private ContentStateView stateView;
    private UpdateManager updateManager;
    private int requestGeneration;
    private String activeUrl;
    private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        client = new CatalogClient();
        buildUi();
        updateManager = new UpdateManager(this);
        updateManager.checkAutomatically();
        loadPage(BASE, "أحدث الإضافات");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(television ? 28 : 14), dp(7), dp(television ? 28 : 10), dp(7));
        header.setBackgroundColor(SURFACE);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(television ? 72 : 58)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(icon, new LinearLayout.LayoutParams(dp(television ? 52 : 40), dp(television ? 52 : 40)));

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        brandBox.setPadding(dp(10), 0, dp(10), 0);
        header.addView(brandBox, new LinearLayout.LayoutParams(0, -1, 1));
        TextView brand = text("LATCHI PLAY", television ? 24 : 18, GOLD, true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        brandBox.addView(brand, new LinearLayout.LayoutParams(-1, 0, 1));
        screenTitle = text("", television ? 13 : 11, Color.rgb(175, 167, 190), false);
        screenTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        brandBox.addView(screenTitle, new LinearLayout.LayoutParams(-1, 0, 1));

        Button search = actionButton("بحث");
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(television ? 110 : 72), dp(television ? 48 : 40)));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(television ? 22 : 7), dp(6), dp(television ? 22 : 7), dp(6));
        nav.setBackgroundColor(Color.rgb(12, 10, 17));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(television ? 68 : 58)));
        addNav(nav, "الرئيسية", v -> loadPage(BASE, "أحدث الإضافات"));
        addNav(nav, "الأفلام", v -> showCategoryDialog(true));
        addNav(nav, "المسلسلات", v -> showCategoryDialog(false));
        addNav(nav, "تحديث", v -> updateManager.checkManually());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(dp(television ? 24 : 4), dp(television ? 16 : 5), dp(television ? 24 : 4), dp(24));
        grid.setItemAnimator(null);
        grid.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        int columns = television ? 5 : 3;
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        adapter = new PosterAdapter(television, this::openDetails);
        grid.setAdapter(adapter);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        stateView = new ContentStateView(this, television);
        content.addView(stateView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void loadPage(String url, String title) {
        if (loading && url.equals(activeUrl)) return;

        screenTitle.setText(title);
        activeUrl = url;
        loading = true;
        int generation = ++requestGeneration;

        if (!DeviceUtils.hasInternetConnection(this)) {
            loading = false;
            showLoadError(url, title, getString(R.string.no_internet));
            return;
        }

        progress.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        stateView.showMessage(getString(R.string.loading_content));

        client.load(url, new CatalogClient.Callback() {
            @Override
            public void onSuccess(List<CatalogItem> items) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    if (items.isEmpty()) {
                        grid.setVisibility(View.GONE);
                        stateView.showMessage(getString(R.string.no_results));
                        return;
                    }

                    stateView.hide();
                    grid.setVisibility(View.VISIBLE);
                    adapter.submit(items);
                    if (television) grid.postDelayed(() -> {
                        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
                        if (holder != null) holder.itemView.requestFocus();
                        else grid.requestFocus();
                    }, 250);
                });
            }

            @Override
            public void onError(CatalogClient.Failure failure) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(generation)) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    String message = failure.type == CatalogClient.FailureType.NETWORK ||
                            failure.type == CatalogClient.FailureType.TIMEOUT
                            ? getString(R.string.no_internet)
                            : getString(R.string.load_content_failed);
                    showLoadError(url, title, message);
                });
            }
        });
    }

    private boolean isCurrentRequest(int generation) {
        return generation == requestGeneration && !isFinishing() && !isDestroyed();
    }

    private void showLoadError(String url, String title, String message) {
        progress.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        stateView.showAction(message, getString(R.string.retry), view -> loadPage(url, title));
    }

    private void openDetails(CatalogItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }

    private void showCategoryDialog(boolean movies) {
        String[] labels = movies
                ? new String[]{"أفلام أجنبية", "أفلام عربية", "أفلام تركية", "أفلام آسيوية", "أفلام هندية", "أنيميشن"}
                : new String[]{"مسلسلات عربية", "مسلسلات تركية", "مسلسلات أجنبية", "مسلسلات آسيوية", "مدبلجة", "قصيرة", "رمضان 2026"};
        String[] slugs = movies
                ? new String[]{"foreign-movies", "arabic-movies", "turkish-movies", "asian-movies", "indian-movies", "animation-movies"}
                : new String[]{"arabic-series", "turkish-series", "foreign-series", "asian-series", "dubbed-series", "short-series", "ramadan-series-2026"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(movies ? "اختر فئة الأفلام" : "اختر فئة المسلسلات")
                .setItems(labels, (d, which) -> loadPage(BASE + slugs[which] + "/", labels[which]))
                .setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(v -> {
            if (television) dialog.getListView().requestFocus();
        });
        dialog.show();
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("اسم الفيلم أو المسلسل");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("البحث")
                .setView(input).setNegativeButton("إلغاء", null).setPositiveButton("بحث", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String query = input.getText().toString().trim();
            if (!query.isEmpty()) {
                loadPage(BASE + "?s=" + Uri.encode(query), "نتائج: " + query);
                dialog.dismiss();
            }
        }));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); return true;
            }
            return false;
        });
        dialog.show();
        input.requestFocus();
        if (!television) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void addNav(LinearLayout nav, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(television ? 16 : 13);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setFocusable(television);
        button.setFocusableInTouchMode(television);
        button.setBackground(pill(Color.rgb(29, 24, 40), dp(14), Color.rgb(75, 58, 96)));
        if (television) {
            button.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(pill(focused ? PURPLE : Color.rgb(29, 24, 40), dp(14), focused ? GOLD : Color.rgb(75, 58, 96)));
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
            });
        }
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(button, p);
    }

    private Button actionButton(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(television ? 15 : 12); b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackground(pill(PURPLE, dp(14), GOLD)); b.setFocusable(television); b.setFocusableInTouchMode(television); return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v;
    }

    private GradientDrawable pill(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(dp(1), stroke); return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
    }

    @Override
    protected void onDestroy() {
        requestGeneration++;
        if (client != null) client.destroy();
        if (updateManager != null) updateManager.destroy();
        super.onDestroy();
    }
}
