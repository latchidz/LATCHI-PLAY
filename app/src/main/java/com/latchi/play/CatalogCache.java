package com.latchi.play;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small internal-file cache for the last successful result of each catalog URL. */
public final class CatalogCache {
    public interface ReadCallback { void onRead(Entry entry); }

    public static final class Entry {
        public final List<CatalogItem> items;
        public final String nextPageUrl;
        public final long savedAt;

        private Entry(List<CatalogItem> items, String nextPageUrl, long savedAt) {
            this.items = Collections.unmodifiableList(items);
            this.nextPageUrl = nextPageUrl;
            this.savedAt = savedAt;
        }
    }

    private final File directory;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public CatalogCache(Context context) {
        directory = new File(context.getFilesDir(), "catalog_cache");
    }

    public void read(String key, ReadCallback callback) {
        ioExecutor.execute(() -> callback.onRead(readInternal(key)));
    }

    public void write(String key, List<CatalogItem> items, String nextPageUrl) {
        List<CatalogItem> snapshot = new ArrayList<>(items);
        ioExecutor.execute(() -> writeInternal(key, snapshot, nextPageUrl));
    }

    private Entry readInternal(String key) {
        try {
            File file = fileFor(key);
            if (!file.isFile()) return null;
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
            JSONObject root = new JSONObject(raw.toString());
            JSONArray array = root.optJSONArray("items");
            if (array == null) return null;
            List<CatalogItem> items = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                String title = item.optString("title", "").trim();
                String pageUrl = item.optString("page_url", "").trim();
                if (title.isEmpty() || pageUrl.isEmpty()) continue;
                items.add(new CatalogItem(
                        title,
                        item.optString("image_url", ""),
                        pageUrl,
                        item.optString("type", "movie"),
                        item.optInt("season_number", 0),
                        item.optInt("episode_number", 0),
                        java.util.Collections.emptyMap()
                ));
            }
            if (items.isEmpty()) return null;
            String next = root.optString("next_page_url", "");
            return new Entry(items, next.isEmpty() ? null : next, root.optLong("saved_at", 0));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeInternal(String key, List<CatalogItem> items, String nextPageUrl) {
        try {
            if (!directory.exists() && !directory.mkdirs()) return;
            JSONArray array = new JSONArray();
            for (CatalogItem item : items) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("image_url", item.imageUrl);
                object.put("page_url", item.pageUrl);
                object.put("type", item.type);
                object.put("season_number", item.seasonNumber);
                object.put("episode_number", item.episodeNumber);
                array.put(object);
            }
            JSONObject root = new JSONObject();
            root.put("saved_at", System.currentTimeMillis());
            root.put("next_page_url", nextPageUrl == null ? "" : nextPageUrl);
            root.put("items", array);

            File target = fileFor(key);
            File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temporary), StandardCharsets.UTF_8))) {
                writer.write(root.toString());
            }
            if (target.exists() && !target.delete()) return;
            //noinspection ResultOfMethodCallIgnored
            temporary.renameTo(target);
        } catch (Exception ignored) {
            // A cache failure must never break the catalog.
        }
    }

    private File fileFor(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte value : hash) name.append(String.format("%02x", value));
        return new File(directory, name + ".json");
    }

    public void destroy() {
        ioExecutor.shutdownNow();
    }
}
