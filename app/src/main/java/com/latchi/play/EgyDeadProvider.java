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
 * EgyDead TV provider (https://tv10.egydead.live).
 *
 * Catalog: parsed from the public WordPress HTML (posters + titles + links,
 * including /episode/ and /season/ URLs).
 * Playback: only direct media URLs literally present in the public HTML are used;
 * the site serves its real stream behind a keyed anti-bot endpoint (data-ajax +
 * data-cp-host) that we deliberately do not reverse-engineer or bypass.
 */
public final class EgyDeadProvider implements ContentProvider {
    public static final String ID = "egydead";
    private static final String BASE = "https://tv10.egydead.live/";

    private static final Pattern CARD = Pattern.compile(
            "<a[^>]+href=\"(https://tv10\\.egydead\\.live/[^\"]+)\"[^>]*title=\"([^\"]*)\"[^>]*>" +
                    "(?:(?!</a>).)*?<img[^>]+src=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEXT_PAGE = Pattern.compile(
            "class=\"page-numbers\"[^>]*href=\"[^\"]*page/(\\d+)/\"", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label(Context context) {
        return "EgyDead";
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
        loadPage(BASE + "home/", page, "", callback);
    }

    @Override
    public void movies(int page, CatalogCallback callback) {
        loadPage(BASE + "page/movies/", page, "movie", callback);
    }

    @Override
    public void series(int page, CatalogCallback callback) {
        loadPage(BASE + "page/series/", page, "series", callback);
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
            String type = lower.contains("/episode/") || lower.contains("/season/")
                    ? "series" : lower.contains("1080p") || lower.contains("bluray")
                    || lower.contains("web-dl") || lower.contains("%d9%85%d8%b4%d8%a7%d9%87%d8%af%d8%a9-%d9%81%d9%8a%d9%84%d9%85")
                    ? "movie" : "series";
            if (!typeFilter.isEmpty() && !type.equals(typeFilter)) continue;

            int season = 0;
            int episode = 0;
            Matcher seasonMatcher = Pattern.compile("الموسم[^\\d]{0,4}(\\d+)").matcher(title);
            if (seasonMatcher.find()) season = Integer.parseInt(seasonMatcher.group(1));
            Matcher episodeMatcher = Pattern.compile("الحلقة[^\\d]{0,4}(\\d+)").matcher(title);
            if (episodeMatcher.find()) episode = Integer.parseInt(episodeMatcher.group(1));
            Matcher codeMatcher = Pattern.compile("[sS](\\d+)[eE](\\d+)").matcher(lower);
            if (codeMatcher.find()) {
                season = Integer.parseInt(codeMatcher.group(1));
                episode = Integer.parseInt(codeMatcher.group(2));
            }
            if (episode > 0 && season <= 0) season = 1;

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("source", "egydead");
            items.add(new CatalogItem(title, image, link, type, season, episode, meta,
                    0L, "", 0f, "", "", "", type, ID, link));
        }
        return items;
    }

    private boolean hasNextPage(String html, int page) {
        Matcher matcher = Pattern.compile("page/(\\d+)/").matcher(html);
        int max = -1;
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value < 50) max = Math.max(max, value);
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

    private PlaybackSource resolveFromPages(CatalogItem item) throws Exception {
        String page = item.pageUrl;
        if (page == null || !page.startsWith("https://tv10.egydead.live/")) return null;

        // The watch page ("مشاهدة فيلم ...") is the natural playback page; also check
        // the item page itself. Only literal direct media in public HTML is used.
        List<String> pagesToCheck = new ArrayList<>();
        pagesToCheck.add(page);
        String watch = findWatchLink(HtmlFetcher.get(page), page);
        if (watch != null && !pagesToCheck.contains(watch)) pagesToCheck.add(watch);

        for (String candidatePage : pagesToCheck) {
            for (String candidate : HtmlFetcher.findDirectMedia(HtmlFetcher.get(candidatePage))) {
                if (HtmlFetcher.isDirectMedia(candidate) && HtmlFetcher.validateMedia(candidate)) {
                    return new PlaybackSource(candidate, mediaType(candidate),
                            Collections.emptyMap(), Collections.singletonMap("origin", "egydead"));
                }
            }
        }
        return null;
    }

    private String findWatchLink(String html, String page) {
        Matcher matcher = Pattern.compile(
                "href=\"(https://tv10\\.egydead\\.live/[^\"]*%d9%85%d8%b4%d8%a7%d9%87%d8%af%d8%a9[^\"]*)\"",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (matcher.find()) {
            String candidate = matcher.group(1);
            if (!candidate.equals(page)) return candidate;
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
