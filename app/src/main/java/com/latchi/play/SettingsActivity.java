package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * App settings: TMDB API key, playback source choice and Xtream credentials.
 * Everything stays on-device (SharedPreferences); nothing is logged.
 */
public class SettingsActivity extends Activity {
    private static final int BG = Color.rgb(7, 6, 12);
    private static final int SURFACE = Color.rgb(18, 15, 25);
    private static final int GOLD = Color.rgb(246, 198, 75);
    private static final int PURPLE = Color.rgb(124, 58, 237);

    private boolean television;
    private AppPrefs prefs;
    private EditText tmdbKey;
    private EditText xtreamServer;
    private EditText xtreamUser;
    private EditText xtreamPassword;
    private EditText peertubeInstance;
    private Button providerButton;
    private android.widget.Switch autoNextSwitch;
    private android.widget.Switch resumeSwitch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        television = DeviceUtils.isTelevision(this);
        setRequestedOrientation(television ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = new AppPrefs(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(television ? 42 : 18), dp(20), dp(television ? 42 : 18), dp(30));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView heading = text(getString(R.string.settings), television ? 26 : 21, GOLD, true);
        heading.setGravity(Gravity.RIGHT);
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        // ---- TMDB section ----
        TextView tmdbLabel = text(getString(R.string.tmdb_key_label), television ? 18 : 15,
                Color.WHITE, true);
        tmdbLabel.setGravity(Gravity.RIGHT);
        tmdbLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(tmdbLabel, new LinearLayout.LayoutParams(-1, -2));

        tmdbKey = field(prefs.getTmdbKey(), getString(R.string.tmdb_key_hint));
        root.addView(tmdbKey, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        TextView tmdbHint = text(getString(R.string.tmdb_key_help), television ? 13 : 11,
                Color.rgb(160, 152, 176), false);
        tmdbHint.setGravity(Gravity.RIGHT);
        tmdbHint.setPadding(0, dp(6), 0, dp(4));
        root.addView(tmdbHint, new LinearLayout.LayoutParams(-1, -2));

        TextView tmdbAttribution = text(getString(R.string.tmdb_attribution), television ? 12 : 10,
                Color.rgb(130, 124, 144), false);
        tmdbAttribution.setGravity(Gravity.RIGHT);
        tmdbAttribution.setPadding(0, dp(4), 0, dp(8));
        root.addView(tmdbAttribution, new LinearLayout.LayoutParams(-1, -2));

        // ---- Playback source section ----
        TextView providerLabel = text(getString(R.string.provider_label), television ? 18 : 15,
                Color.WHITE, true);
        providerLabel.setGravity(Gravity.RIGHT);
        providerLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(providerLabel, new LinearLayout.LayoutParams(-1, -2));

        providerButton = new Button(this);
        providerButton.setAllCaps(false);
        providerButton.setTextColor(Color.WHITE);
        providerButton.setTextSize(television ? 16 : 14);
        providerButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        providerButton.setGravity(Gravity.CENTER | Gravity.RIGHT);
        providerButton.setBackground(pill(SURFACE, dp(14), GOLD));
        providerButton.setOnClickListener(v -> chooseProvider());
        root.addView(providerButton, new LinearLayout.LayoutParams(-1, dp(television ? 60 : 52)));

        // ---- Xtream section ----
        TextView xtreamLabel = text(getString(R.string.xtream_section), television ? 18 : 15,
                Color.WHITE, true);
        xtreamLabel.setGravity(Gravity.RIGHT);
        xtreamLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(xtreamLabel, new LinearLayout.LayoutParams(-1, -2));

        xtreamServer = field(prefs.getXtreamServer(), getString(R.string.xtream_server_hint));
        root.addView(xtreamServer, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        xtreamUser = field(prefs.getXtreamUser(), getString(R.string.xtream_user));
        root.addView(xtreamUser, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        xtreamPassword = field(prefs.getXtreamPassword(), getString(R.string.xtream_pass));
        xtreamPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(xtreamPassword, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        TextView xtreamNote = text(getString(R.string.xtream_note), television ? 13 : 11,
                Color.rgb(160, 152, 176), false);
        xtreamNote.setGravity(Gravity.RIGHT);
        xtreamNote.setPadding(0, dp(6), 0, dp(4));
        root.addView(xtreamNote, new LinearLayout.LayoutParams(-1, -2));

        // ---- PeerTube section ----
        TextView peerLabel = text(getString(R.string.peertube_section), television ? 18 : 15,
                Color.WHITE, true);
        peerLabel.setGravity(Gravity.RIGHT);
        peerLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(peerLabel, new LinearLayout.LayoutParams(-1, -2));

        peertubeInstance = field(prefs.getPeerTubeInstance(), getString(R.string.peertube_instance));
        root.addView(peertubeInstance, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        // ---- Playback section ----
        TextView playbackLabel = text(getString(R.string.playback_section), television ? 18 : 15,
                Color.WHITE, true);
        playbackLabel.setGravity(Gravity.RIGHT);
        playbackLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(playbackLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout autoNextRow = new LinearLayout(this);
        autoNextRow.setGravity(Gravity.CENTER_VERTICAL);
        autoNextRow.setOrientation(LinearLayout.HORIZONTAL);
        autoNextRow.setBackground(pill(SURFACE, dp(12), Color.rgb(58, 48, 74)));
        root.addView(autoNextRow, new LinearLayout.LayoutParams(-1, dp(television ? 58 : 52)));

        autoNextSwitch = new android.widget.Switch(this);
        autoNextSwitch.setChecked(prefs.autoNext());
        autoNextSwitch.setFocusable(television);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                dp(television ? 90 : 70), -1);
        switchParams.setMargins(dp(10), 0, dp(6), 0);
        autoNextRow.addView(autoNextSwitch, switchParams);

        TextView autoNextText = text(getString(R.string.auto_next_label),
                television ? 16 : 14, Color.WHITE, false);
        autoNextText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        autoNextRow.addView(autoNextText, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout resumeRow = new LinearLayout(this);
        resumeRow.setGravity(Gravity.CENTER_VERTICAL);
        resumeRow.setOrientation(LinearLayout.HORIZONTAL);
        resumeRow.setBackground(pill(SURFACE, dp(12), Color.rgb(58, 48, 74)));
        LinearLayout.LayoutParams resumeRowParams = new LinearLayout.LayoutParams(-1,
                dp(television ? 58 : 52));
        resumeRowParams.setMargins(0, dp(8), 0, 0);
        root.addView(resumeRow, resumeRowParams);

        resumeSwitch = new android.widget.Switch(this);
        resumeSwitch.setChecked(prefs.resumePlayback());
        resumeSwitch.setFocusable(television);
        LinearLayout.LayoutParams resumeSwitchParams = new LinearLayout.LayoutParams(
                dp(television ? 90 : 70), -1);
        resumeSwitchParams.setMargins(dp(10), 0, dp(6), 0);
        resumeRow.addView(resumeSwitch, resumeSwitchParams);

        TextView resumeText = text(getString(R.string.resume_playback_label),
                television ? 16 : 14, Color.WHITE, false);
        resumeText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        resumeRow.addView(resumeText, new LinearLayout.LayoutParams(0, -1, 1));

        // ---- App section ----
        TextView appLabel = text(getString(R.string.app_section), television ? 18 : 15,
                Color.WHITE, true);
        appLabel.setGravity(Gravity.RIGHT);
        appLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(appLabel, new LinearLayout.LayoutParams(-1, -2));

        Button clearHistory = new Button(this);
        clearHistory.setText(R.string.clear_history);
        clearHistory.setAllCaps(false);
        clearHistory.setTextColor(Color.WHITE);
        clearHistory.setTextSize(television ? 16 : 14);
        clearHistory.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        clearHistory.setBackground(pill(Color.rgb(24, 20, 32), dp(12), Color.rgb(150, 60, 60)));
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        root.addView(clearHistory, new LinearLayout.LayoutParams(-1, dp(television ? 56 : 50)));

        TextView about = text(getString(R.string.about_line, BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE), television ? 13 : 11, Color.rgb(160, 152, 176), false);
        about.setGravity(Gravity.RIGHT);
        about.setPadding(0, dp(10), 0, dp(2));
        root.addView(about, new LinearLayout.LayoutParams(-1, -2));

        // ---- Actions ----
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(television ? 70 : 62)));

        Button save = new Button(this);
        save.setText(R.string.save);
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setTextSize(television ? 17 : 15);
        save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        save.setBackground(pill(PURPLE, dp(14), GOLD));
        save.setOnClickListener(v -> saveAll());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                television ? dp(220) : 0, -1, television ? 0 : 1);
        saveParams.setMargins(0, dp(16), 0, dp(16));
        actions.addView(save, saveParams);

        Button back = new Button(this);
        back.setText(R.string.back);
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(television ? 17 : 15);
        back.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        back.setBackground(pill(Color.rgb(24, 20, 32), dp(14), Color.rgb(68, 54, 86)));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                television ? dp(180) : 0, -1, television ? 0 : 1);
        backParams.setMargins(television ? dp(16) : 0, dp(16), 0, dp(16));
        actions.addView(back, backParams);

        refreshProviderButton();
    }

    private void chooseProvider() {
        String[] labels = {
                getString(R.string.provider_archive),
                getString(R.string.provider_peertube),
                getString(R.string.provider_xtream),
                getString(R.string.provider_topcinemaa),
                getString(R.string.provider_egydead),
                getString(R.string.provider_mycima)
        };
        String[] ids = {
                AppPrefs.PROVIDER_ARCHIVE,
                AppPrefs.PROVIDER_PEERTUBE,
                AppPrefs.PROVIDER_XTREAM,
                AppPrefs.PROVIDER_TOPCINEMAA,
                AppPrefs.PROVIDER_EGYDEAD,
                AppPrefs.PROVIDER_MYCIMA
        };
        int current = 0;
        String providerId = prefs.getProviderId();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(providerId)) current = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.provider_label)
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    prefs.setProviderId(ids[which]);
                    refreshProviderButton();
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void refreshProviderButton() {
        String id = prefs.getProviderId();
        String label;
        if (AppPrefs.PROVIDER_XTREAM.equals(id)) label = getString(R.string.provider_xtream);
        else if (AppPrefs.PROVIDER_PEERTUBE.equals(id)) label = getString(R.string.provider_peertube);
        else if (AppPrefs.PROVIDER_TOPCINEMAA.equals(id)) label = getString(R.string.provider_topcinemaa);
        else if (AppPrefs.PROVIDER_EGYDEAD.equals(id)) label = getString(R.string.provider_egydead);
        else if (AppPrefs.PROVIDER_MYCIMA.equals(id)) label = getString(R.string.provider_mycima);
        else label = getString(R.string.provider_archive);
        providerButton.setText("◆  " + label);
    }

    private void saveAll() {
        prefs.setTmdbKey(tmdbKey.getText().toString());
        prefs.setXtreamServer(xtreamServer.getText().toString());
        prefs.setXtreamUser(xtreamUser.getText().toString());
        prefs.setXtreamPassword(xtreamPassword.getText().toString());
        prefs.setPeerTubeInstance(peertubeInstance.getText().toString());
        prefs.setAutoNext(autoNextSwitch.isChecked());
        prefs.setResumePlayback(resumeSwitch.isChecked());
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_history_title)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear_history, (d, which) -> {
                    new HistoryStore(this).clearAll();
                    Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText field(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(120, 114, 134));
        input.setTextSize(television ? 16 : 14);
        input.setBackground(round(Color.rgb(13, 10, 18), dp(12), Color.rgb(58, 48, 74)));
        input.setPadding(dp(14), 0, dp(14), 0);
        return input;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setLineSpacing(0, 1.2f);
        return view;
    }

    private GradientDrawable pill(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
