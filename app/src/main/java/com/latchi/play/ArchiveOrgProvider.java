package com.latchi.play;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Free, legal source: Internet Archive (archive.org).
 * Searches the public collection and returns a direct mp4 URL that Media3 can play.
 * Verified in practice: archive.org serves mp4 with HTTP Range support (206).
 */
public final class ArchiveOrgProvider implements ContentProvider {
    private static final String SEARCH = "https://archive.org/advancedsearch.php";
    private static final String METADATA = "https://archive.org/metadata/";
    private static final String DOWNLOAD = "https://archive.org/download/";
    private static final int MAX_IDENTIFIERS = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public String id() {
        return AppPrefs.PROVIDER_ARCHIVE;
    }

    @Override
    public String label(Context context) {
        return "Archive.org";
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
        String title = cleanTitle(item);
        if (title.isEmpty()) return null;

        List<String> identifiers = searchIdentifiers(title, item.year);
        if (identifiers.isEmpty() && !item.year.isEmpty()) {
            identifiers = searchIdentifiers(title, "");
        }
        for (int i = 0; i < identifiers.size() && i < MAX_IDENTIFIERS; i++) {
            String file = bestFile(identifiers.get(i));
            if (file == null) continue;
            String url = DOWNLOAD + identifiers.get(i) + "/" + encodePath(file);
            if (validateMedia(url)) {
                java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
                meta.put("origin", "archive.org");
                meta.put("identifier", identifiers.get(i));
                meta.put("file", file);
                return new PlaybackSource(url, "mp4", Collections.emptyMap(), meta);
            }
        }
        return null;
    }

    private String cleanTitle(CatalogItem item) {
        String title = item.title == null ? "" : item.title.trim();
        if (title.isEmpty()) return "";
        // Strip common episode markers so search stays useful for movies.
        return title.replaceAll("\\s+S\\d+\\s*E\\d+.*$", "").trim();
    }

    private List<String> searchIdentifiers(String title, String year) throws Exception {
        StringBuilder q = new StringBuilder("title:(")
                .append(quote(title))
                .append(") AND mediatype:movies AND format:\"MPEG4\"");
        if (!year.isEmpty()) {
            q.append(" AND year:").append(year);
        }
        String url = SEARCH + "?q=" + encode(q.toString())
                + "&fl[]=identifier&rows=8&output=json";

        HttpURLConnection connection = null;
        try {
            connection = open(url);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return Collections.emptyList();
            JSONObject root = new JSONObject(readBody(connection));
            JSONObject response = root.optJSONObject("response");
            if (response == null) return Collections.emptyList();
            JSONArray docs = response.optJSONArray("docs");
            if (docs == null) return Collections.emptyList();
            List<String> identifiers = new ArrayList<>();
            for (int i = 0; i < docs.length(); i++) {
                String id = docs.optJSONObject(i).optString("identifier", "").trim();
                if (!id.isEmpty()) identifiers.add(id);
            }
            return identifiers;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Picks the best playable mp4 file from an archive.org item. */
    private String bestFile(String identifier) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = open(METADATA + encodePath(identifier));
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            JSONObject root = new JSONObject(readBody(connection));
            JSONArray files = root.optJSONArray("files");
            if (files == null) return null;

            String firstMpeg4 = null;
            long firstSize = -1;
            String preferred512 = null;
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                String format = f.optString("format", "");
                if (!"MPEG4".equals(format) && !"512Kb MPEG4".equals(format)) continue;
                String name = f.optString("name", "");
                if (name.isEmpty() || name.endsWith(".txt")) continue;
                if ("512Kb MPEG4".equals(format)) {
                    if (preferred512 == null) preferred512 = name;
                } else {
                    long size = f.optLong("size", 0L);
                    if (firstMpeg4 == null) {
                        firstMpeg4 = name;
                        firstSize = size;
                    } else if (size > firstSize) {
                        firstMpeg4 = name;
                        firstSize = size;
                    }
                }
            }
            return preferred512 != null ? preferred512 : firstMpeg4;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean validateMedia(String mediaUrl) {
        HttpURLConnection connection = null;
        try {
            connection = open(mediaUrl);
            connection.setRequestProperty("Range", "bytes=0-1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) return false;
            try (InputStream stream = connection.getInputStream()) {
                stream.read();
            }
            return true;
        } catch (Exception error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", "*/*");
        return connection;
    }

    private String readBody(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
                if (body.length() > 4_000_000) throw new Exception("archive response too large");
            }
        }
        return body.toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "") + "\"";
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception error) {
            return "";
        }
    }

    /** Encodes a file path preserving slashes as needed for archive.org download URLs. */
    private static String encodePath(String value) {
        StringBuilder out = new StringBuilder();
        try {
            for (char c : value.toCharArray()) {
                if (c == ' ') out.append("%20");
                else if (c == '&') out.append("%26");
                else if (c == '?') out.append("%3F");
                else if (c == '#') out.append("%23");
                else if (c == '+') out.append("%2B");
                else out.append(c);
            }
        } catch (Exception ignored) {
            return value;
        }
        return out.toString();
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }
}
