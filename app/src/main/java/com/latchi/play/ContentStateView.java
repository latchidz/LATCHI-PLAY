package com.latchi.play;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable empty/error state used by network-backed screens. */
public final class ContentStateView extends LinearLayout {
    private final TextView messageView;
    private final Button actionButton;
    private final boolean television;

    public ContentStateView(Context context, boolean television) {
        super(context);
        this.television = television;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(28), dp(28), dp(28), dp(28));
        setBackgroundColor(Color.rgb(7, 6, 12));
        setVisibility(GONE);

        messageView = new TextView(context);
        messageView.setTextColor(Color.rgb(218, 213, 226));
        messageView.setTextSize(television ? 22 : 17);
        messageView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(0, 1.25f);
        addView(messageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        actionButton = new Button(context);
        actionButton.setAllCaps(false);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTextSize(television ? 17 : 14);
        actionButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        actionButton.setBackground(buttonBackground(false));
        actionButton.setFocusable(television);
        actionButton.setFocusableInTouchMode(television);
        actionButton.setVisibility(GONE);
        if (television) {
            actionButton.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(buttonBackground(focused));
                float scale = focused ? 1.06f : 1f;
                view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            });
        }
        LayoutParams buttonParams = new LayoutParams(dp(television ? 230 : 190), dp(television ? 58 : 50));
        buttonParams.setMargins(0, dp(22), 0, 0);
        addView(actionButton, buttonParams);
    }

    public void showMessage(CharSequence message) {
        messageView.setText(message);
        actionButton.setVisibility(GONE);
        setVisibility(VISIBLE);
    }

    public void showAction(CharSequence message, CharSequence action, View.OnClickListener listener) {
        messageView.setText(message);
        actionButton.setText(action);
        actionButton.setOnClickListener(listener);
        actionButton.setVisibility(VISIBLE);
        setVisibility(VISIBLE);
        if (television) actionButton.post(actionButton::requestFocus);
    }

    public void hide() {
        setVisibility(GONE);
        actionButton.setOnClickListener(null);
    }

    private GradientDrawable buttonBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? Color.rgb(124, 58, 237) : Color.rgb(36, 29, 48));
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(focused ? 2 : 1), focused ? Color.rgb(246, 198, 75) : Color.rgb(78, 61, 99));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
