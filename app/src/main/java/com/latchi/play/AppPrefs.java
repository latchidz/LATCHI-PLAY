package com.latchi.play;

import android.content.Context;
import android.content.SharedPreferences;

/** Central settings storage (TMDB key, playback provider, Xtream credentials). */
public final class AppPrefs {
    public static final String PROVIDER_ARCHIVE = "archive";
    public static final String PROVIDER_PEERTUBE = "peertube";
    public static final String PROVIDER_XTREAM = "xtream";
    public static final String PROVIDER_TOPCINEMAA = "topcinemaa";
    public static final String PROVIDER_EGYDEAD = "egydead";
    public static final String PROVIDER_MYCIMA = "mycima";

    private static final String PREFS = "latchi_play_settings";
    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean autoNext() {
        return prefs.getBoolean("auto_next", true);
    }

    public void setAutoNext(boolean enabled) {
        prefs.edit().putBoolean("auto_next", enabled).apply();
    }

    public boolean resumePlayback() {
        return prefs.getBoolean("resume_playback", true);
    }

    public void setResumePlayback(boolean enabled) {
        prefs.edit().putBoolean("resume_playback", enabled).apply();
    }

    public String getTmdbKey() {
        return prefs.getString("tmdb_key", "").trim();
    }

    public void setTmdbKey(String value) {
        prefs.edit().putString("tmdb_key", value == null ? "" : value.trim()).apply();
    }

    public boolean hasTmdbKey() {
        return !getTmdbKey().isEmpty();
    }

    public String getProviderId() {
        return prefs.getString("provider", PROVIDER_ARCHIVE);
    }

    public void setProviderId(String value) {
        prefs.edit().putString("provider", value == null ? PROVIDER_ARCHIVE : value).apply();
    }

    public String getXtreamServer() {
        return prefs.getString("xtream_server", "").trim();
    }

    public void setXtreamServer(String value) {
        prefs.edit().putString("xtream_server", value == null ? "" : value.trim()).apply();
    }

    public String getXtreamUser() {
        return prefs.getString("xtream_user", "").trim();
    }

    public void setXtreamUser(String value) {
        prefs.edit().putString("xtream_user", value == null ? "" : value.trim()).apply();
    }

    public String getXtreamPassword() {
        return prefs.getString("xtream_pass", "").trim();
    }

    public void setXtreamPassword(String value) {
        prefs.edit().putString("xtream_pass", value == null ? "" : value.trim()).apply();
    }

    public boolean hasXtream() {
        return !getXtreamServer().isEmpty() && !getXtreamUser().isEmpty();
    }

    public String getPeerTubeInstance() {
        String value = prefs.getString("peertube_instance", "").trim();
        return value.isEmpty() ? "https://framatube.org" : value;
    }

    public void setPeerTubeInstance(String value) {
        prefs.edit().putString("peertube_instance", value == null ? "" : value.trim()).apply();
    }
}
