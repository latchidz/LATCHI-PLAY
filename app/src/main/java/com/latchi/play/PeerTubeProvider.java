package com.latchi.play;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Free, open-source source: PeerTube (public API).
 * The instance URL is configurable in Settings; defaults to framatube.org.
 */
public final class PeerTubeProvider implements ContentProvider {
    private final AppPrefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public PeerTubeProvider(Context context) {
        prefs = new AppPrefs(context);
    }

    @Override
    public String id() {
        return AppPrefs.PROVIDER_PEERTUBE;
    }

    @Override
    public String label(Context context) {
        return "PeerTube";
    }

    @Override
    public boolean isConfigured(Context context) {
        return true;
    }

    @Override
    public void resolve(CatalogItem item, Callback callback) {
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
        String title = item.title == null ? "" : item.title.trim();
        if (title.isEmpty()) return null;
        String instance = prefs.getPeerTubeInstance();
        return resolveWithInstance(instance, title);
    }

    private PlaybackSource resolveWithInstance(String instance, String title) throws Exception {
        String base = instance.endsWith("/") ? instance.substring(0, instance.length() - 1) : instance;
        String url = base + "/api/v1/search/videos?search=" + encode(title)
                + "&sort=-publishedAt&nsfw=false";

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
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
                    if (body.length() > 2_000_000) return null;
                }
            }
            JSONObject root = new JSONObject(body.toString());
            JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) return null;

            JSONObject video = data.optJSONObject(0);
            if (video == null) return null;

            // Prefer a direct mp4 file <= 720p, otherwise fall back to HLS.
            JSONArray files = video.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.optJSONObject(i);
                    if (f == null) continue;
                    JSONObject resolution = f.optJSONObject("resolution");
                    int resolutionId = resolution != null ? resolution.optInt("id", 999) : 999;
                    String fileUrl = f.optString("fileUrl", "");
                    if (!fileUrl.isEmpty() && resolutionId <= 720) {
                        return new PlaybackSource(fileUrl, "mp4", Collections.emptyMap(),
                                Collections.singletonMap("origin", "peertube"));
                    }
                }
            }
            JSONArray playlists = video.optJSONArray("streamingPlaylists");
            if (playlists != null && playlists.length() > 0) {
                String playlistUrl = playlists.optJSONObject(0).optString("playlistUrl", "");
                if (!playlistUrl.isEmpty()) {
                    return new PlaybackSource(playlistUrl, "hls", Collections.emptyMap(),
                            Collections.singletonMap("origin", "peertube"));
                }
            }
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
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
