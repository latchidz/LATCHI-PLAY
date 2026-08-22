package com.latchi.play;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xtream Codes API provider for the user's own IPTV server (configured in Settings).
 * Standard public Xtream endpoints: player_api.php (VOD/series) + stream URLs.
 */
public final class XtreamProvider implements ContentProvider {
    private final AppPrefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public XtreamProvider(Context context) {
        prefs = new AppPrefs(context);
    }

    @Override
    public String id() {
        return AppPrefs.PROVIDER_XTREAM;
    }

    @Override
    public String label(Context context) {
        return "Xtream";
    }

    @Override
    public boolean isConfigured(Context context) {
        return prefs.hasXtream();
    }

    @Override
    public void resolve(CatalogItem item, Callback callback) {
        if (!prefs.hasXtream()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                PlaybackSource source = resolveTitle(item);
                if (destroyed.get()) return;
                if (source != null) callback.onResolved(source, label(null));
                else callback.onError();
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private PlaybackSource resolveTitle(CatalogItem item) throws Exception {
        String query = item.title == null ? "" : item.title.trim();
        if (query.isEmpty()) return null;

        String server = normalizeServer(prefs.getXtreamServer());
        String username = prefs.getXtreamUser();
        String password = prefs.getXtreamPassword();
        String api = server + "/player_api.php?username=" + encode(username)
                + "&password=" + encode(password);

        boolean series = "series".equals(item.type) || "tv".equals(item.mediaType);
        if (series) {
            return resolveSeries(api, query, username, password);
        }
        return resolveVod(api, query, username, password);
    }

    private PlaybackSource resolveVod(String api, String query, String username, String password)
            throws Exception {
        JSONArray streams = getJsonArray(api + "&action=get_vod_streams");
        int best = -1;
        String bestName = null;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject s = streams.optJSONObject(i);
            if (s == null) continue;
            String name = s.optString("name", "");
            if (name.isEmpty()) continue;
            int score = matchScore(name, query);
            if (score <= 0) continue;
            if (best < 0 || score > best) {
                best = score;
                bestName = s.optString("stream_id", "");
            }
        }
        if (bestName == null || bestName.isEmpty()) return null;
        String url = normalizeServer(prefs.getXtreamServer()) + "/movie/" + encode(username)
                + "/" + encode(password) + "/" + bestName + ".m3u8";
        return new PlaybackSource(url, "hls", Collections.emptyMap(),
                Collections.singletonMap("origin", "xtream"));
    }

    private PlaybackSource resolveSeries(String api, String query, String username, String password)
            throws Exception {
        JSONArray series = getJsonArray(api + "&action=get_series");
        int best = -1;
        String bestId = null;
        for (int i = 0; i < series.length(); i++) {
            JSONObject s = series.optJSONObject(i);
            if (s == null) continue;
            String name = s.optString("name", "");
            if (name.isEmpty()) continue;
            int score = matchScore(name, query);
            if (score <= 0) continue;
            if (best < 0 || score > best) {
                best = score;
                bestId = s.optString("series_id", "");
            }
        }
        if (bestId == null || bestId.isEmpty()) return null;

        // Grab the first episode of the first season available.
        JSONObject info = getJsonObject(api + "&action=get_series_info&series_id=" + bestId);
        JSONArray seasons = info.optJSONArray("seasons");
        if (seasons != null && seasons.length() > 0) {
            JSONObject firstSeason = seasons.optJSONObject(0);
            JSONArray episodes = firstSeason == null ? null : firstSeason.optJSONArray("episodes");
            if (episodes != null && episodes.length() > 0) {
                String streamId = episodes.optJSONObject(0).optString("id", "");
                if (!streamId.isEmpty()) {
                    String url = normalizeServer(prefs.getXtreamServer()) + "/series/" + encode(username)
                            + "/" + encode(password) + "/" + streamId + ".m3u8";
                    return new PlaybackSource(url, "hls", Collections.emptyMap(),
                            Collections.singletonMap("origin", "xtream"));
                }
            }
        }
        return null;
    }

    private JSONArray getJsonArray(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return new JSONArray();
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 10_000_000) return new JSONArray();
                }
            }
            return new JSONArray(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject getJsonObject(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 10_000_000) return null;
                }
            }
            return new JSONObject(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int matchScore(String name, String query) {
        String a = name.toLowerCase();
        String b = query.toLowerCase();
        if (a.equals(b)) return 100;
        if (a.startsWith(b)) return 80;
        if (a.contains(b)) return 60;
        // Token overlap
        String[] tokens = b.split("\\s+");
        int hits = 0;
        for (String token : tokens) {
            if (token.length() < 3) continue;
            if (a.contains(token)) hits++;
        }
        return hits > 0 ? 40 + hits : 0;
    }

    private String normalizeServer(String server) {
        String value = server == null ? "" : server.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return value;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception error) {
            return "";
        }
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }
}
