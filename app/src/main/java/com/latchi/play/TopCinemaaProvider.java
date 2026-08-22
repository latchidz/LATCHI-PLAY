package com.latchi.play;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TopCinemaa provider (https://topcinemaa.co).
 *
 * Catalog: parsed from the public WordPress HTML (posters + titles + links).
 * Playback: only direct media URLs literally present in the public HTML are used;
 * the site serves its real stream through a protected third-party embed
 * (down.vidtube.one) which we deliberately do not follow or decrypt.
 */
public final class TopCinemaaProvider implements ContentProvider {
    public static final String ID = "topcinemaa";
    private static final String BASE = "https://topcinemaa.co/";

    private static final Pattern CARD = Pattern.compile(
            "<a[^>]+href=\"(https://topcinemaa\\.co/[^\"]+)\"[^>]*title=\"([^\"]*)\"[^>]*>" +
                    "(?:(?!</a>).)*?<img[^>]+(?:data-src|src)=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEXT_PAGE = Pattern.compile(
            "href=\"https://topcinemaa\\.co/page/(\\d+)/\"", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label(Context context) {
        return "TopCinemaa";
    }

    @Override
    public boolean isConfigured(Context context) {
        return true;
    }

    @Override
    public boolean supportsCatalog() {
        return true;
    }

    @Override
    public void home(int page, CatalogCallback callback) {
        loadPage(pageUrl("", page), page, "", callback);
    }

    @Override
    public void movies(int page, CatalogCallback callback) {
        loadPage(pageUrl("", page), page, "movie", callback);
    }

    @Override
    public void series(int page, CatalogCallback callback) {
        loadPage(pageUrl("", page), page, "series", callback);
    }

    @Override
    public void search(String query, int page, CatalogCallback callback) {
        String safe = query == null ? "" : query.trim();
        if (safe.isEmpty()) {
            callback.onError();
            return;
        }
        loadPage(BASE + "?s=" + urlEncode(safe), page, "", callback);
    }

    private String pageUrl(String path, int page) {
        return BASE + (page <= 1 ? "" : "page/" + page + "/");
    }

    private void loadPage(final String url, final int page, final String typeFilter,
                          final CatalogCallback callback) {
        if (destroyed.get()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                String html = HtmlFetcher.get(url);
                if (destroyed.get()) return;
                List<CatalogItem> items = parseCards(html, typeFilter);
                if (destroyed.get()) return;
                boolean hasMore = !items.isEmpty() && page < 40 && hasNextPage(html, page);
                callback.onSuccess(items, hasMore);
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private List<CatalogItem> parseCards(String html, String typeFilter) {
        List<CatalogItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = CARD.matcher(html);
        while (matcher.find() && items.size() < 60) {
            String link = matcher.group(1).trim();
            String title = HtmlFetcher.decode(matcher.group(2));
            String image = matcher.group(3).trim();
            if (title.isEmpty() || !seen.add(link)) continue;

            String lower = link.toLowerCase(Locale.US);
            String type = lower.contains("%d9%85%d8%b3%d9%84%d8%b3%d9%84") || lower.contains("series")
                    ? "series" : "movie";
            if (lower.contains("%d8%a7%d9%86%d9%85%d9%8a") || lower.contains("anime")) {
                type = "series";
            }
            if (!typeFilter.isEmpty() && !type.equals(typeFilter)) continue;

            int season = 0;
            int episode = 0;
            Matcher seasonMatcher = Pattern.compile("الموسم[^\\d]{0,4}(\\d+)").matcher(title);
            if (seasonMatcher.find()) season = Integer.parseInt(seasonMatcher.group(1));
            Matcher episodeMatcher = Pattern.compile("الحلقة[^\\d]{0,4}(\\d+)").matcher(title);
            if (episodeMatcher.find()) episode = Integer.parseInt(episodeMatcher.group(1));
            if (episode > 0 && season <= 0) season = 1;

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("source", "topcinemaa");
            items.add(new CatalogItem(title, image, link, type, season, episode, meta,
                    0L, "", 0f, "", "", "", type, ID, link));
        }
        return items;
    }

    private boolean hasNextPage(String html, int page) {
        Matcher matcher = NEXT_PAGE.matcher(html);
        int max = -1;
        while (matcher.find()) {
            try {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            } catch (Exception ignored) {
                // skip
            }
        }
        return max > page;
    }

    @Override
    public void resolve(CatalogItem item, Callback callback) {
        if (destroyed.get()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                PlaybackSource source = resolveFromPages(item);
                if (destroyed.get()) return;
                if (source != null) callback.onResolved(source, label(null));
                else callback.onError();
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    /** Checks the public item page (and the public embedScreen view) for direct media only. */
    private PlaybackSource resolveFromPages(CatalogItem item) throws Exception {
        String page = item.pageUrl;
        if (page == null || !page.startsWith("https://topcinemaa.co/")) {
            return null;
        }
        List<String> media = HtmlFetcher.findDirectMedia(HtmlFetcher.get(page));
        if (media.isEmpty()) {
            String embedScreen = page + (page.contains("?") ? "&" : "?") + "embedScreen=true";
            try {
                media = HtmlFetcher.findDirectMedia(HtmlFetcher.get(embedScreen));
            } catch (Exception ignored) {
                // The embedScreen view is often an embed page; if it is not readable, give up.
            }
        }
        for (String candidate : media) {
            if (HtmlFetcher.isDirectMedia(candidate) && HtmlFetcher.validateMedia(candidate)) {
                return new PlaybackSource(candidate, mediaType(candidate),
                        Collections.emptyMap(), Collections.singletonMap("origin", "topcinemaa"));
            }
        }
        return null;
    }

    private String mediaType(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) return "hls";
        if (lower.contains(".mpd")) return "dash";
        return "mp4";
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception error) {
            return value;
        }
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }
}
