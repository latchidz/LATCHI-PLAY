package com.latchi.play;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local playback history and continue-watching metadata. */
public final class HistoryStore {
    private static final String PREFS = "playback_history";
    private static final String KEY_ITEMS = "items_json";
    private static final int MAX_ENTRIES = 100;
    private final SharedPreferences preferences;

    public HistoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<HistoryEntry> getAll() {
        List<HistoryEntry> entries = read();
        entries.sort((left, right) -> Long.compare(right.lastPlayedAt, left.lastPlayedAt));
        return entries;
    }

    public synchronized void markOpened(CatalogItem item) {
        List<HistoryEntry> entries = read();
        int index = find(entries, item.pageUrl);
        long position = 0;
        long duration = 0;
        if (index >= 0) {
            HistoryEntry existing = entries.remove(index);
            position = existing.positionMs;
            duration = existing.durationMs;
        }
        entries.add(0, new HistoryEntry(item, position, duration, System.currentTimeMillis()));
        write(entries);
    }

    public synchronized void update(CatalogItem item, long positionMs, long durationMs) {
        List<HistoryEntry> entries = read();
        int index = find(entries, item.pageUrl);
        if (index >= 0) entries.remove(index);
        entries.add(0, new HistoryEntry(item, positionMs, durationMs, System.currentTimeMillis()));
        write(entries);
    }

    public synchronized void remove(CatalogItem item) {
        List<HistoryEntry> entries = read();
        int index = find(entries, item.pageUrl);
        if (index >= 0) {
            entries.remove(index);
            write(entries);
        }
    }

    private List<HistoryEntry> read() {
        List<HistoryEntry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_ITEMS, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) continue;
                String title = object.optString("title", "").trim();
                String pageUrl = object.optString("page_url", "").trim();
                if (title.isEmpty() || pageUrl.isEmpty()) continue;
                String type = object.optString("type", "movie");
                long tmdbId = object.optLong("tmdb_id", 0L);
                if (tmdbId <= 0) tmdbId = CatalogItem.tmdbIdFromPageUrl(pageUrl);
                String mediaType = object.optString("media_type", "");
                if (mediaType.isEmpty()) mediaType = CatalogItem.mediaTypeFromPageUrl(pageUrl);
                CatalogItem item = new CatalogItem(
                        title,
                        object.optString("image_url", ""),
                        pageUrl,
                        type,
                        object.optInt("season_number", 0),
                        object.optInt("episode_number", 0),
                        Collections.emptyMap(),
                        tmdbId,
                        object.optString("overview", ""),
                        (float) object.optDouble("rating", 0d),
                        object.optString("year", ""),
                        "",
                        object.optString("genres", ""),
                        mediaType);
                result.add(new HistoryEntry(item, object.optLong("position_ms", 0),
                        object.optLong("duration_ms", 0), object.optLong("last_played_at", 0)));
            }
        } catch (Exception ignored) {
            // Corrupt local history behaves like an empty history.
        }
        return result;
    }

    private void write(List<HistoryEntry> entries) {
        JSONArray array = new JSONArray();
        int limit = Math.min(entries.size(), MAX_ENTRIES);
        for (int index = 0; index < limit; index++) {
            HistoryEntry entry = entries.get(index);
            try {
                JSONObject object = new JSONObject();
                object.put("title", entry.item.title);
                object.put("image_url", entry.item.imageUrl);
                object.put("page_url", entry.item.pageUrl);
                object.put("type", entry.item.type);
                object.put("season_number", entry.item.seasonNumber);
                object.put("episode_number", entry.item.episodeNumber);
                object.put("tmdb_id", entry.item.tmdbId);
                object.put("media_type", entry.item.mediaType);
                object.put("overview", entry.item.overview);
                object.put("rating", entry.item.rating);
                object.put("year", entry.item.year);
                object.put("genres", entry.item.genres);
                object.put("position_ms", entry.positionMs);
                object.put("duration_ms", entry.durationMs);
                object.put("last_played_at", entry.lastPlayedAt);
                array.put(object);
            } catch (Exception ignored) {
                // Skip malformed entries without losing the rest.
            }
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private int find(List<HistoryEntry> entries, String pageUrl) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).item.pageUrl.equals(pageUrl)) return index;
        }
        return -1;
    }
}
