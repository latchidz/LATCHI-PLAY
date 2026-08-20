package com.latchi.play;

import android.text.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves only direct HTTPS media URLs explicitly present in the public content HTML.
 * It intentionally does not execute/decrypt scripts, open third-party server pages, or bypass access controls.
 */
public final class ServerResolver {
    public interface Callback {
        void onResolved(Result result);
        void onError();
    }

    public static final class Result {
        public final List<ServerInfo> servers;
        public final List<PlaybackSource> sources;

        private Result(List<ServerInfo> servers, List<PlaybackSource> sources) {
            this.servers = Collections.unmodifiableList(servers);
            this.sources = Collections.unmodifiableList(sources);
        }
    }

    private static final String CONTENT_HOST = "shooflive.net";
    private static final int MAX_HTML_CHARS = 5_000_000;
    private static final int MAX_MEDIA_CANDIDATES = 5;
    private static final Pattern IFRAME = Pattern.compile(
            "<iframe\\s+[^>]*(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_MEDIA = Pattern.compile(
            "https://[^\\s\\\"'<>\\\\]+?\\.(?:m3u8|mp4|mpd)(?:\\?[^\\s\\\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public void resolve(String contentUrl, Callback callback) {
        if (destroyed.get() || !isAllowedContentUrl(contentUrl)) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                String html = downloadContentPage(contentUrl);
                if (destroyed.get()) return;
                URI base = URI.create(contentUrl);
                List<ServerInfo> servers = discoverServers(html, base);
                List<PlaybackSource> sources = discoverAndValidateMedia(html);
                if (!destroyed.get()) callback.onResolved(new Result(servers, sources));
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private String downloadContentPage(String contentUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(contentUrl).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) LATCHI-PLAY/3.1");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400 ||
                    !isAllowedContentUrl(connection.getURL().toString())) throw new IOException("content");

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append('\n');
                    if (html.length() > MAX_HTML_CHARS) throw new IOException("response");
                }
            }
            return html.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<ServerInfo> discoverServers(String html, URI base) {
        List<ServerInfo> servers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = IFRAME.matcher(html);
        int priority = 0;
        while (matcher.find() && servers.size() < 20) {
            try {
                String raw = decode(matcher.group(1));
                URI resolved = base.resolve(raw);
                if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) continue;
                String url = resolved.toString();
                if (!seen.add(url)) continue;
                servers.add(new ServerInfo("Server " + (++priority), url, priority,
                        Collections.singletonMap("kind", "iframe")));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed public iframe URLs.
            }
        }
        return servers;
    }

    private List<PlaybackSource> discoverAndValidateMedia(String html) {
        List<PlaybackSource> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = DIRECT_MEDIA.matcher(html);
        while (matcher.find() && seen.size() < MAX_MEDIA_CANDIDATES) {
            String candidate = trimUrl(decode(matcher.group()));
            if (!isDirectMediaUrl(candidate) || !seen.add(candidate)) continue;
            if (validateMedia(candidate)) {
                sources.add(new PlaybackSource(candidate, mediaType(candidate),
                        Collections.emptyMap(), Collections.singletonMap("origin", "content_html")));
            }
        }
        return sources;
    }

    private boolean validateMedia(String mediaUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(mediaUrl).openConnection();
            connection.setConnectTimeout(6_000);
            connection.setReadTimeout(6_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Range", "bytes=0-1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) return false;
            URL finalUrl = connection.getURL();
            if (!"https".equalsIgnoreCase(finalUrl.getProtocol())) return false;
            try (InputStream stream = connection.getInputStream()) {
                // Opening the stream validates that the response body is accessible; no content is retained.
                stream.read();
            }
            return true;
        } catch (SocketTimeoutException error) {
            return false;
        } catch (IOException error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isAllowedContentUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null &&
                    (host.equalsIgnoreCase(CONTENT_HOST) || host.toLowerCase().endsWith("." + CONTENT_HOST));
        } catch (Exception error) {
            return false;
        }
    }

    private boolean isDirectMediaUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd");
        } catch (Exception error) {
            return false;
        }
    }

    private String mediaType(String url) {
        String path = URI.create(url).getPath().toLowerCase();
        if (path.endsWith(".m3u8")) return "hls";
        if (path.endsWith(".mpd")) return "dash";
        return "mp4";
    }

    private String trimUrl(String value) {
        return value.replaceAll("[),;\\]}]+$", "");
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
