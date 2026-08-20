package com.latchi.play;

import android.text.Html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogClient {
    public interface Callback {
        void onSuccess(List<CatalogItem> items);
        void onError(String message);
    }

    private static final Pattern CARD_LINK = Pattern.compile(
            "<a\\s+[^>]*href=[\\\"']([^\\\"']+/(?:movies|series|episode)/[^\\\"']+)[\\\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_ATTR = Pattern.compile("title=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_ATTR = Pattern.compile("(?:data-src|src)=[\\\"']?([^\\s\\\"'>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTR = Pattern.compile("alt=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public void load(String pageUrl, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(pageUrl).openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(18_000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) LATCHI-PLAY/3.0");
                connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 400) throw new Exception("HTTP " + status);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line).append('\n');
                reader.close();
                List<CatalogItem> items = parse(html.toString());
                if (items.isEmpty()) throw new Exception("EMPTY");
                callback.onSuccess(items);
            } catch (Exception error) {
                callback.onError(error.getMessage() == null ? "NETWORK" : error.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private List<CatalogItem> parse(String html) {
        List<CatalogItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher links = CARD_LINK.matcher(html);
        while (links.find() && result.size() < 80) {
            String url = decode(links.group(1));
            if (!seen.add(url)) continue;
            int start = links.start();
            int end = Math.min(html.length(), links.end() + 1800);
            String block = html.substring(start, end);

            Matcher titleMatcher = TITLE_ATTR.matcher(block.substring(0, Math.min(block.length(), 500)));
            Matcher imageMatcher = IMAGE_ATTR.matcher(block);
            String title = titleMatcher.find() ? decode(titleMatcher.group(1)) : "";
            String image = "";
            while (imageMatcher.find()) {
                String candidate = decode(imageMatcher.group(1));
                if (candidate.startsWith("http") && !candidate.contains("data:image")) {
                    image = candidate;
                    break;
                }
            }
            if (title.isEmpty()) {
                Matcher alt = ALT_ATTR.matcher(block);
                if (alt.find()) title = decode(alt.group(1));
            }
            if (title.isEmpty() || image.isEmpty()) {
                seen.remove(url);
                continue;
            }
            String type = url.contains("/movies/") ? "movie" : url.contains("/series/") ? "series" : "episode";
            result.add(new CatalogItem(title, image, url, type));
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private String decode(String value) {
        return Html.fromHtml(value == null ? "" : value, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    public void destroy() {
        executor.shutdownNow();
    }
}
