package com.latchi.play;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal TMDB v3 client (public, authorized API) used for the catalog UI:
 * trending, popular, search, details and season/episode listings.
 */
public final class TmdbClient {
    public interface Callback<T> {
        void onSuccess(T value);
        void onError();
    }

    public static final class Genre {
        public final int id;
        public final String name;

        Genre(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMAGE = "https://image.tmdb.org/t/p/";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    private final AppPrefs prefs;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public TmdbClient(Context context) {
        prefs = new AppPrefs(context);
    }

    public boolean isConfigured() {
        return prefs.hasTmdbKey();
    }

    public void trending(int page, Callback<List<CatalogItem>> callback) {
        executeList("/trending/all/week", "page=" + Math.max(1, page), "", callback);
    }

    public void popular(String mediaType, int page, Callback<List<CatalogItem>> callback) {
        boolean tv = "tv".equals(mediaType);
        executeList(tv ? "/tv/popular" : "/movie/popular", "page=" + Math.max(1, page),
                tv ? "tv" : "movie", callback);
    }

    public void search(String query, int page, Callback<List<CatalogItem>> callback) {
        String safe = query == null ? "" : query.trim();
        if (safe.isEmpty()) {
            callback.onError();
            return;
        }
        executeList("/search/multi", "query=" + encode(safe) + "&page=" + Math.max(1, page),
                "", callback);
    }

    public void discover(String mediaType, int genreId, int page,
                         Callback<List<CatalogItem>> callback) {
        boolean tv = "tv".equals(mediaType);
        String path = tv ? "/discover/tv" : "/discover/movie";
        String query = "with_genres=" + genreId + "&page=" + Math.max(1, page);
        executeList(path, query, tv ? "tv" : "movie", callback);
    }

    public void genreList(String mediaType, Callback<List<Genre>> callback) {
        boolean tv = "tv".equals(mediaType);
        String path = tv ? "/genre/tv/list" : "/genre/movie/list";
        execute(path, "", new JsonTask<List<Genre>>() {
            @Override
            public List<Genre> parse(JSONObject root) throws Exception {
                JSONArray genres = root.optJSONArray("genres");
                if (genres == null) return Collections.emptyList();
                List<Genre> result = new ArrayList<>();
                for (int i = 0; i < genres.length(); i++) {
                    JSONObject g = genres.optJSONObject(i);
                    if (g == null) continue;
                    int id = g.optInt("id", 0);
                    String name = g.optString("name", "").trim();
                    if (id > 0 && !name.isEmpty()) result.add(new Genre(id, name));
                }
                return result;
            }
        }, callback);
    }

    public void details(CatalogItem item, Callback<TmdbDetail> callback) {
        if (item == null || item.tmdbId <= 0) {
            callback.onError();
            return;
        }
        final boolean tv = "tv".equals(item.mediaType) || "series".equals(item.type);
        final long id = item.tmdbId;
        execute(tv ? "/tv/" + id : "/movie/" + id, "", new JsonTask<TmdbDetail>() {
            @Override
            public TmdbDetail parse(JSONObject root) throws Exception {
                return parseDetail(root, tv);
            }
        }, callback);
    }

    public void episodes(long tvId, int seasonNumber, Callback<List<CatalogItem>> callback) {
        if (tvId <= 0 || seasonNumber <= 0) {
            callback.onError();
            return;
        }
        final long id = tvId;
        final int season = seasonNumber;
        execute("/tv/" + id + "/season/" + season, "", new JsonTask<List<CatalogItem>>() {
            @Override
            public List<CatalogItem> parse(JSONObject root) throws Exception {
                JSONArray episodes = root.optJSONArray("episodes");
                if (episodes == null) return Collections.emptyList();
                List<CatalogItem> result = new ArrayList<>();
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject e = episodes.optJSONObject(i);
                    if (e == null) continue;
                    int number = e.optInt("episode_number", 0);
                    if (number <= 0) continue;
                    String name = e.optString("name", "").trim();
                    String display = name.isEmpty() ? "الحلقة " + number : name;
                    String still = e.optString("still_path", "");
                    java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
                    meta.put("tmdb_tv_id", String.valueOf(id));
                    meta.put("tmdb_season", String.valueOf(season));
                    meta.put("tmdb_episode_id", String.valueOf(e.optLong("id", 0L)));
                    CatalogItem episode = new CatalogItem(display,
                            TmdbClient.posterUrl(still, 500),
                            "tmdb:tv:" + id + ":s" + season + "e" + number,
                            "episode", season, number, meta,
                            id, e.optString("overview", ""), 0f,
                            e.optString("air_date", ""), "", "", "tv");
                    result.add(episode);
                }
                return result;
            }
        }, callback);
    }

    public static String posterUrl(String path, int width) {
        return imageUrl(path, "w" + width);
    }

    public static String backdropUrl(String path) {
        return imageUrl(path, "w1280");
    }

    private static String imageUrl(String path, String size) {
        if (path == null || path.isEmpty()) return "";
        return IMAGE + size + path;
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ impl

    private interface JsonTask<T> {
        T parse(JSONObject root) throws Exception;
    }

    private <T> void execute(final String path, final String query, final JsonTask<T> task,
                             final Callback<T> callback) {
        if (destroyed.get() || !isConfigured()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject root = getJson(path, query);
                if (destroyed.get()) return;
                T value = task.parse(root);
                if (!destroyed.get()) callback.onSuccess(value);
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private void executeList(final String path, final String query, final String fallbackMediaType,
                             final Callback<List<CatalogItem>> callback) {
        execute(path, query, root -> parseResults(root, fallbackMediaType), callback);
    }

    private List<CatalogItem> parseResults(JSONObject root, String fallbackMediaType)
            throws Exception {
        JSONArray results = root.optJSONArray("results");
        if (results == null) return Collections.emptyList();
        List<CatalogItem> items = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            String mediaType = r.optString("media_type", "");
            if (mediaType.isEmpty()) mediaType = fallbackMediaType;
            if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) continue;
            long id = r.optLong("id", 0L);
            if (id <= 0) continue;
            String title = r.optString(mediaType.equals("tv") ? "name" : "title", "").trim();
            if (title.isEmpty()) continue;
            String date = r.optString(mediaType.equals("tv") ? "first_air_date" : "release_date", "");
            String year = date.length() >= 4 ? date.substring(0, 4) : "";
            CatalogItem item = CatalogItem.fromTmdb(id, mediaType, title,
                    r.optString("poster_path", ""),
                    r.optString("backdrop_path", ""),
                    (float) r.optDouble("vote_average", 0d),
                    year,
                    r.optString("overview", ""));
            items.add(item);
        }
        return items;
    }

    private TmdbDetail parseDetail(JSONObject root, boolean tv) throws Exception {
        long id = root.optLong("id", 0L);
        String title = root.optString(tv ? "name" : "title", "").trim();
        String date = root.optString(tv ? "first_air_date" : "release_date", "");
        String year = date.length() >= 4 ? date.substring(0, 4) : "";
        String overview = root.optString("overview", "").trim();

        StringBuilder genres = new StringBuilder();
        JSONArray genreArray = root.optJSONArray("genres");
        if (genreArray != null) {
            for (int i = 0; i < genreArray.length(); i++) {
                JSONObject g = genreArray.optJSONObject(i);
                if (g == null) continue;
                String name = g.optString("name", "").trim();
                if (!name.isEmpty()) {
                    if (genres.length() > 0) genres.append(" • ");
                    genres.append(name);
                }
            }
        }

        int runtime = 0;
        if (!tv) runtime = root.optInt("runtime", 0);

        List<TmdbDetail.TmdbSeason> seasons = new ArrayList<>();
        if (tv) {
            JSONArray seasonArray = root.optJSONArray("seasons");
            if (seasonArray != null) {
                for (int i = 0; i < seasonArray.length(); i++) {
                    JSONObject s = seasonArray.optJSONObject(i);
                    if (s == null) continue;
                    int number = s.optInt("season_number", 0);
                    if (number <= 0) continue;
                    seasons.add(new TmdbDetail.TmdbSeason(number,
                            s.optString("name", "").trim(),
                            s.optInt("episode_count", 0),
                            s.optString("poster_path", "")));
                }
            }
        }

        return new TmdbDetail(id, tv ? "tv" : "movie", title, overview,
                (float) root.optDouble("vote_average", 0d), year, genres.toString(),
                posterUrl(root.optString("poster_path", ""), 500),
                backdropUrl(root.optString("backdrop_path", "")),
                runtime, seasons);
    }

    private JSONObject getJson(String path, String query) throws Exception {
        String key = prefs.getTmdbKey();
        boolean bearer = key.contains(".");

        StringBuilder url = new StringBuilder(API).append(path).append('?');
        if (query != null && !query.isEmpty()) url.append(query);
        if (bearer) {
            url.append(query != null && !query.isEmpty() ? '&' : "").append("language=en-US");
        } else {
            url.append(query != null && !query.isEmpty() ? '&' : "").append("api_key=").append(encode(key));
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url.toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            if (bearer) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("tmdb status " + status);

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > MAX_RESPONSE_CHARS) throw new IOException("tmdb response");
                }
            }
            return new JSONObject(body.toString());
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
}
