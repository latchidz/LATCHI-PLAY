package com.latchi.play;

import android.text.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared HTTP helper for web-site providers.
 *
 * It only reads public web pages. Playback discovery is restricted to direct
 * media URLs explicitly present in the returned HTML (.mp4/.m3u8/.mpd). It does
 * not execute JavaScript, follow embed iframes, or bypass any protection.
 */
public final class HtmlFetcher {
    private static final Pattern DIRECT_MEDIA = Pattern.compile(
            "https?://[^\\s\"'<>\\\\]+?\\.(?:m3u8|mp4|mpd|webm)(?:\\?[^\\s\"'<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE);

    private HtmlFetcher() {
    }

    /** Fetches a public page as UTF-8 text. Throws IOException on HTTP/network failure. */
    public static String get(String url) throws IOException {
        return get(url, 4_000_000);
    }

    public static String get(String url, int maxChars) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(18_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("http " + status);

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                    if (body.length() > maxChars) throw new IOException("response too large");
                }
            }
            return body.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Direct media URLs found literally in the HTML — the only playback path. */
    public static List<String> findDirectMedia(String html) {
        if (html == null) return java.util.Collections.emptyList();
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = DIRECT_MEDIA.matcher(html);
        while (matcher.find() && seen.size() < 6) {
            String candidate = matcher.group().replaceAll("[),;\\]}]+$", "");
            if (seen.add(candidate)) result.add(candidate);
        }
        return result;
    }

    /** Returns true when the URL is a direct media URL by path suffix. */
    public static boolean isDirectMedia(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            return (uri.getScheme() != null) && (path.endsWith(".m3u8") || path.endsWith(".mp4")
                    || path.endsWith(".mpd") || path.endsWith(".webm"));
        } catch (Exception error) {
            return false;
        }
    }

    /** Validates a media URL with a tiny Range request (used before playback). */
    public static boolean validateMedia(String mediaUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(mediaUrl).openConnection();
            connection.setConnectTimeout(6_000);
            connection.setReadTimeout(6_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LATCHI-PLAY/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Range", "bytes=0-1");
            int status = connection.getResponseCode();
            return status >= 200 && status < 400;
        } catch (SocketTimeoutException error) {
            return false;
        } catch (IOException error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    public static String decode(String value) {
        if (value == null) return "";
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
