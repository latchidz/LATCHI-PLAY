package com.latchi.play;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistent local favorites with migration support for the legacy URL booleans. */
public final class FavoritesStore {
    private static final String PREFS = "favorites_catalog";
    private static final String KEY_ITEMS = "items_json";
    private static final String LEGACY_PREFS = "favorites";

    private final Context context;
    private final SharedPreferences preferences;

    public FavoritesStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<CatalogItem> getAll() {
        return readItems();
    }

    public synchronized boolean isFavorite(CatalogItem item) {
        List<CatalogItem> items = readItems();
        if (find(items, item.pageUrl) >= 0) return true;

        boolean legacyFavorite = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .getBoolean(item.pageUrl, false);
        if (legacyFavorite) {
            items.add(0, item);
            writeItems(items);
        }
        return legacyFavorite;
    }

    /** @return true when the item is favorite after the operation. */
    public synchronized boolean toggle(CatalogItem item) {
        List<CatalogItem> items = readItems();
        int index = find(items, item.pageUrl);
        boolean favorite;
        if (index >= 0) {
            items.remove(index);
            favorite = false;
        } else {
            items.add(0, item);
            favorite = true;
        }
        writeItems(items);
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(item.pageUrl, favorite).apply();
        return favorite;
    }

    public synchronized void remove(CatalogItem item) {
        List<CatalogItem> items = readItems();
        int index = find(items, item.pageUrl);
        if (index >= 0) {
            items.remove(index);
            writeItems(items);
        }
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(item.pageUrl, false).apply();
    }

    private List<CatalogItem> readItems() {
        List<CatalogItem> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_ITEMS, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) continue;
                String title = object.optString("title", "").trim();
                String pageUrl = object.optString("page_url", "").trim();
                if (title.isEmpty() || pageUrl.isEmpty()) continue;
                result.add(new CatalogItem(
                        title,
                        object.optString("image_url", ""),
                        pageUrl,
                        object.optString("type", "movie"),
                        object.optInt("season_number", 0),
                        object.optInt("episode_number", 0),
                        java.util.Collections.emptyMap()
                ));
            }
        } catch (Exception ignored) {
            // A damaged local value behaves like an empty list.
        }
        return result;
    }

    private void writeItems(List<CatalogItem> items) {
        JSONArray array = new JSONArray();
        for (CatalogItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("image_url", item.imageUrl);
                object.put("page_url", item.pageUrl);
                object.put("type", item.type);
                object.put("season_number", item.seasonNumber);
                object.put("episode_number", item.episodeNumber);
                array.put(object);
            } catch (Exception ignored) {
                // Skip only the malformed item; keep the rest of the favorites.
            }
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private int find(List<CatalogItem> items, String pageUrl) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).pageUrl.equals(pageUrl)) return index;
        }
        return -1;
    }
}
