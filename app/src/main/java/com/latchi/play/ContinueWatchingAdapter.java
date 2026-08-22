package com.latchi.play;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

/** Horizontal "Continue Watching" row shown on the home feed. */
public final class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.Holder> {
    public interface Listener {
        void onOpen(HistoryEntry entry);
    }

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final Listener listener;
    private final boolean television;

    public ContinueWatchingAdapter(boolean television, Listener listener) {
        this.television = television;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<HistoryEntry> data) {
        entries.clear();
        entries.addAll(data);
        notifyDataSetChanged();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).item.pageUrl.hashCode();
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        int width = dp(context, television ? 150 : 118);
        int height = dp(context, television ? 250 : 200);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(television);
        card.setFocusableInTouchMode(television);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setBackground(cardBackground(false));
        card.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(width, height);
        params.setMargins(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
        card.setLayoutParams(params);

        FrameLayout imageBox = new FrameLayout(context);
        card.addView(imageBox, new LinearLayout.LayoutParams(-1, 0, 1));
        ImageView poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageBox.addView(poster, new FrameLayout.LayoutParams(-1, -1));

        ProgressBar progressBar = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(context, 3),
                Gravity.BOTTOM);
        imageBox.addView(progressBar, progressParams);

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(television ? 13 : 11);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.RIGHT);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5));
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        Holder holder = new Holder(card, poster, title, progressBar);
        if (television) {
            card.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(cardBackground(focused));
                view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(140).start();
            });
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HistoryEntry entry = entries.get(position);
        holder.title.setText(entry.item.title);
        holder.progressBar.setProgress(entry.progressPercent());
        holder.poster.setBackgroundColor(Color.rgb(12, 10, 17));
        Glide.with(holder.poster).load(entry.item.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .centerCrop()
                .into(holder.poster);
        holder.itemView.setContentDescription(entry.item.title);
        holder.itemView.setOnClickListener(v -> listener.onOpen(entry));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;
        final ProgressBar progressBar;

        Holder(View view, ImageView poster, TextView title, ProgressBar progressBar) {
            super(view);
            this.poster = poster;
            this.title = title;
            this.progressBar = progressBar;
        }
    }

    private static GradientDrawable cardBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? Color.rgb(37, 29, 50) : Color.rgb(20, 17, 28));
        d.setCornerRadius(16);
        d.setStroke(focused ? 3 : 1, focused ? Color.rgb(246, 198, 75) : Color.rgb(68, 54, 86));
        return d;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
