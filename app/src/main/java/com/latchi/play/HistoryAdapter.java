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

public final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
    public interface Listener { void onOpen(HistoryEntry entry); }
    public interface LongListener { void onRemove(HistoryEntry entry); }

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final boolean television;
    private final Listener listener;
    private final LongListener longListener;

    public HistoryAdapter(boolean television, Listener listener, LongListener longListener) {
        this.television = television;
        this.listener = listener;
        this.longListener = longListener;
        setHasStableIds(true);
    }

    public void submit(List<HistoryEntry> data) {
        entries.clear();
        entries.addAll(data);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return entries.size(); }
    @Override public long getItemId(int position) { return entries.get(position).item.pageUrl.hashCode(); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(background(false));
        card.setFocusable(television);
        card.setFocusableInTouchMode(television);
        card.setClickable(true);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(context, television ? 350 : 248));
        int margin = dp(context, television ? 12 : 5);
        params.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(params);

        FrameLayout imageFrame = new FrameLayout(context);
        card.addView(imageFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        ImageView poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageFrame.addView(poster, new FrameLayout.LayoutParams(-1, -1));

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(246, 198, 75)));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(45, 37, 56)));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(context, 5), Gravity.BOTTOM);
        imageFrame.addView(progress, progressParams);

        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(television ? 15 : 13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        title.setMaxLines(2);
        title.setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5));
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(context, television ? 56 : 52)));

        Holder holder = new Holder(card, poster, title, progress);
        if (television) {
            card.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(background(focused));
                float scale = focused ? 1.06f : 1f;
                view.animate().scaleX(scale).scaleY(scale).translationZ(focused ? dp(context, 10) : 0)
                        .setDuration(140).start();
            });
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HistoryEntry entry = entries.get(position);
        holder.title.setText(entry.item.title);
        holder.progress.setProgress(entry.progressPercent());
        holder.progress.setContentDescription(entry.progressPercent() + "%");
        Glide.with(holder.poster).load(entry.item.imageUrl).diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.darker_gray).error(android.R.color.darker_gray)
                .centerCrop().into(holder.poster);
        holder.itemView.setContentDescription(entry.item.title);
        holder.itemView.setOnClickListener(view -> listener.onOpen(entry));
        holder.itemView.setOnLongClickListener(view -> {
            longListener.onRemove(entry);
            return true;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView poster; final TextView title; final ProgressBar progress;
        Holder(View root, ImageView poster, TextView title, ProgressBar progress) {
            super(root); this.poster = poster; this.title = title; this.progress = progress;
        }
    }

    private static GradientDrawable background(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? Color.rgb(37, 29, 50) : Color.rgb(20, 17, 28));
        drawable.setCornerRadius(18);
        drawable.setStroke(focused ? 4 : 1,
                focused ? Color.rgb(246, 198, 75) : Color.rgb(68, 54, 86));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
