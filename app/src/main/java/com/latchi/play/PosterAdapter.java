package com.latchi.play;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {
    public interface Listener { void onOpen(CatalogItem item); }

    private final List<CatalogItem> items = new ArrayList<>();
    private final Listener listener;
    private final boolean television;

    public PosterAdapter(boolean television, Listener listener) {
        this.television = television;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<CatalogItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) { return items.get(position).pageUrl.hashCode(); }
    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        int cardHeight = dp(context, television ? 310 : 274);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setBackground(cardBackground(false));
        card.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        RecyclerView.LayoutParams rootParams = new RecyclerView.LayoutParams(-1, cardHeight);
        int margin = dp(context, television ? 9 : 5);
        rootParams.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(rootParams);

        FrameLayout imageBox = new FrameLayout(context);
        card.addView(imageBox, new LinearLayout.LayoutParams(-1, 0, 1));
        ImageView poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageBox.addView(poster, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(context);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(television ? 12 : 10);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        badge.setBackground(pill(Color.rgb(124, 58, 237), dp(context, 9)));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        badgeParams.setMargins(0, dp(context, 9), dp(context, 9), 0);
        imageBox.addView(badge, badgeParams);

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(television ? 15 : 13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(context, 10), dp(context, 7), dp(context, 10), dp(context, 7));
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(context, television ? 58 : 54)));

        Holder holder = new Holder(card, poster, title, badge);
        card.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(cardBackground(focused));
            float scale = focused ? 1.07f : 1f;
            view.animate().scaleX(scale).scaleY(scale).translationZ(focused ? dp(context, 12) : 0)
                    .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            if (focused) view.bringToFront();
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CatalogItem item = items.get(position);
        holder.title.setText(item.title);
        holder.badge.setText(item.type.equals("movie") ? "فيلم" : item.type.equals("series") ? "مسلسل" : "حلقة");
        Glide.with(holder.poster).load(item.imageUrl).diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.darker_gray).centerCrop().into(holder.poster);
        holder.itemView.setOnClickListener(v -> listener.onOpen(item));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView poster; final TextView title; final TextView badge;
        Holder(View view, ImageView poster, TextView title, TextView badge) {
            super(view); this.poster = poster; this.title = title; this.badge = badge;
        }
    }

    private static GradientDrawable cardBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? Color.rgb(37, 29, 50) : Color.rgb(20, 17, 28));
        d.setCornerRadius(18);
        d.setStroke(focused ? 4 : 1, focused ? Color.rgb(246, 198, 75) : Color.rgb(68, 54, 86));
        return d;
    }

    private static GradientDrawable pill(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
}
