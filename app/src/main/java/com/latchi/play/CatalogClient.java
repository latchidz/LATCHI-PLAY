package com.latchi.play;

import android.text.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogClient {
    public enum FailureType {
        INVALID_URL,
        NETWORK,
        TIMEOUT,
        HTTP,
        RESPONSE
    }

    public static final class Failure {
        public final FailureType type;
        public final int httpStatus;

        private Failure(FailureType type, int httpStatus) {
            this.type = type;
            this.httpStatus = httpStatus;
        }

        public static Failure of(FailureType type) {
            return new Failure(type, 0);
        }

        public static Failure http(int status) {
            return new Failure(FailureType.HTTP, status);
        }
    }

    public interface Callback {
        void onSuccess(CatalogPage page);
        void onError(Failure failure);
    }

    private static final String ALLOWED_HOST = "shooflive.net";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 18_000;
    private static final int MAX_RESPONSE_CHARS = 5_000_000;

    private static final Pattern CARD_LINK = Pattern.compile(
            "<a\\s+[^>]*href=[\\\"']([^\\\"']+/(?:movies|series|episode)/[^\\\"']+)[\\\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_ATTR = Pattern.compile("title=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_ATTR = Pattern.compile("(?:data-src|src)=[\\\"']?([^\\s\\\"'>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTR = Pattern.compile("alt=[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_LINK = Pattern.compile(
            "<a\\s+[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public void load(String pageUrl, Callback callback) {
        if (destroyed.get()) return;
        if (!isAllowedUrl(pageUrl)) {
            callback.onError(Failure.of(FailureType.INVALID_URL));
            return;
        }

        executor.execute(() -> {
            if (destroyed.get()) return;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(pageUrl).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) LATCHI-PLAY/3.1");
                connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 400) {
                    dispatchError(callback, Failure.http(status));
                    return;
                }
                if (!isAllowedUrl(connection.getURL().toString())) {
                    dispatchError(callback, Failure.of(FailureType.INVALID_URL));
                    return;
                }

                StringBuilder html = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        html.append(line).append('\n');
                        if (html.length() > MAX_RESPONSE_CHARS) {
                            dispatchError(callback, Failure.of(FailureType.RESPONSE));
                            return;
                        }
                    }
                }

                String document = html.toString();
                List<CatalogItem> items = parse(document);
                String nextPageUrl = findNextPageUrl(document);
                if (!destroyed.get()) callback.onSuccess(new CatalogPage(items, nextPageUrl));
            } catch (SocketTimeoutException error) {
                dispatchError(callback, Failure.of(FailureType.TIMEOUT));
            } catch (UnknownHostException error) {
                dispatchError(callback, Failure.of(FailureType.NETWORK));
            } catch (IOException error) {
                dispatchError(callback, Failure.of(FailureType.NETWORK));
            } catch (RuntimeException error) {
                dispatchError(callback, Failure.of(FailureType.RESPONSE));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void dispatchError(Callback callback, Failure failure) {
        if (!destroyed.get()) callback.onError(failure);
    }

    private List<CatalogItem> parse(String html) {
        List<CatalogItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher links = CARD_LINK.matcher(html);
        while (links.find() && result.size() < 80) {
            String url = decode(links.group(1));
            if (!isAllowedUrl(url) || !seen.add(url)) continue;

            int start = links.start();
            int end = Math.min(html.length(), links.end() + 1800);
            String block = html.substring(start, end);
            String linkTag = block.substring(0, Math.min(block.length(), 500));

            Matcher titleMatcher = TITLE_ATTR.matcher(linkTag);
            String title = titleMatcher.find() ? decode(titleMatcher.group(1)) : "";
            if (title.isEmpty()) {
                Matcher altMatcher = ALT_ATTR.matcher(block);
                if (altMatcher.find()) title = decode(altMatcher.group(1));
            }
            if (title.isEmpty()) {
                seen.remove(url);
                continue;
            }

            String image = "";
            Matcher imageMatcher = IMAGE_ATTR.matcher(block);
            while (imageMatcher.find()) {
                String candidate = decode(imageMatcher.group(1));
                if (isAllowedImageUrl(candidate)) {
                    image = candidate;
                    break;
                }
            }

            String type = url.contains("/movies/") ? "movie" :
                    url.contains("/series/") ? "series" : "episode";
            result.add(new CatalogItem(title, image, url, type));
        }
        return result;
    }

    private String findNextPageUrl(String html) {
        Matcher matcher = NEXT_LINK.matcher(html);
        while (matcher.find()) {
            String label = decode(matcher.group(2).replaceAll("<[^>]+>", "")).trim();
            if (!(label.equals("›") || label.equalsIgnoreCase("next") || label.equals("التالي"))) continue;
            String candidate = decode(matcher.group(1));
            if (isAllowedUrl(candidate)) return candidate;
        }
        return null;
    }

    private boolean isAllowedUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equalsIgnoreCase(ALLOWED_HOST) || host.toLowerCase().endsWith("." + ALLOWED_HOST));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean isAllowedImageUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String decode(String value) {
        return Html.fromHtml(value == null ? "" : value, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }
}
