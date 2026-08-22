package com.latchi.play;

import android.content.Context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * Catalog, details and season/episode listings come from the public WordPress
 * HTML (posters, titles, links, og meta). Playback uses only direct media URLs
 * literally present in the public HTML; the site's real stream sits behind a
 * protected embed (down.vidtube.one) that we deliberately do not follow.
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
    private static final Pattern ALL_HREFS = Pattern.compile(
            "href=\"(https://topcinemaa\\.co/[^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEASON_CARD = Pattern.compile(
            "Small--Box Season.{0,400}?<span>الموسم</span>(\\d+).{0,600}?data-src=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Map<String, Integer> AR_SEASON = new LinkedHashMap<>();

    static {
        AR_SEASON.put("الاول", 1);
        AR_SEASON.put("الأول", 1);
        AR_SEASON.put("الثاني", 2);
        AR_SEASON.put("الثاني", 2);
        AR_SEASON.put("الثالث", 3);
        AR_SEASON.put("الرابع", 4);
        AR_SEASON.put("الخامس", 5);
        AR_SEASON.put("السادس", 6);
        AR_SEASON.put("السابع", 7);
        AR_SEASON.put("الثامن", 8);
        AR_SEASON.put("التاسع", 9);
        AR_SEASON.put("العاشر", 10);
        AR_SEASON.put("الحادي عشر", 11);
        AR_SEASON.put("الثاني عشر", 12);
        AR_SEASON.put("الثالث عشر", 13);
        AR_SEASON.put("الرابع عشر", 14);
        AR_SEASON.put("الخامس عشر", 15);
        AR_SEASON.put("السادس عشر", 16);
        AR_SEASON.put("السابع عشر", 17);
        AR_SEASON.put("الثامن عشر", 18);
        AR_SEASON.put("التاسع عشر", 19);
        AR_SEASON.put("العشرون", 20);
    }

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

            String type = classify(title, link);
            if (!typeFilter.isEmpty() && !type.equals(typeFilter)) continue;

            int[] se = parseSeasonEpisode(title);
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("source", "topcinemaa");
            items.add(new CatalogItem(title, image, link, type, se[0], se[1], meta,
                    0L, "", 0f, "", "", "", type, ID, link));
        }
        return items;
    }

    private String classify(String title, String link) {
        String lower = link.toLowerCase(Locale.US) + " " + title;
        if (lower.contains("فيلم")) return "movie";
        return "series";
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

    // ------------------------------------------------------------------ details

    @Override
    public void details(CatalogItem item, DetailsCallback callback) {
        if (destroyed.get()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                MediaDetail detail = parseDetails(item);
                if (destroyed.get()) return;
                if (detail != null) callback.onSuccess(detail);
                else callback.onError();
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private MediaDetail parseDetails(CatalogItem item) throws Exception {
        String page = item.pageUrl;
        if (page == null || !page.startsWith(BASE)) return null;
        String html = HtmlFetcher.get(page);
        String title = SiteMeta.og(html, "og:title");
        title = title.replaceAll("\\s+(?:توب سينما|اون لاين)$", "").trim();
        if (title.isEmpty()) title = item.title;
        String description = SiteMeta.og(html, "og:description");
        String poster = SiteMeta.og(html, "og:image");
        float rating = SiteMeta.rating(html);
        int ratingCount = SiteMeta.ratingCount(html);
        String year = SiteMeta.year(title + " " + description);
        String genres = SiteMeta.genres(title + " " + description);
        List<String> cast = SiteMeta.cast(description);
        return new MediaDetail(ID, page, item.type, title, "", poster, poster,
                description, year, rating, ratingCount, genres, "", "", 0, cast, "",
                "");
    }

    // ------------------------------------------------------------------ episodes

    @Override
    public void episodes(CatalogItem item, EpisodesCallback callback) {
        if (destroyed.get()) {
            callback.onError();
            return;
        }
        executor.execute(() -> {
            try {
                List<SeasonGroup> seasons = parseEpisodes(item);
                if (destroyed.get()) return;
                if (seasons.isEmpty()) callback.onError();
                else callback.onSuccess(seasons);
            } catch (Exception error) {
                if (!destroyed.get()) callback.onError();
            }
        });
    }

    private List<SeasonGroup> parseEpisodes(CatalogItem item) throws Exception {
        String page = item.pageUrl;
        if (page == null || !page.startsWith(BASE)) return Collections.emptyList();
        String html = HtmlFetcher.get(page);
        String seriesKey = keyOf(decode(page));
        if (seriesKey.isEmpty()) return Collections.emptyList();

        Map<Integer, String> seasonPosters = new LinkedHashMap<>();
        Matcher seasonMatcher = SEASON_CARD.matcher(html);
        while (seasonMatcher.find()) {
            try {
                seasonPosters.put(Integer.parseInt(seasonMatcher.group(1)), seasonMatcher.group(2));
            } catch (Exception ignored) {
                // skip malformed season card
            }
        }

        Map<Integer, SeasonGroup.Builder> builders = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> seenEpisodeKeys = new LinkedHashSet<>();
        Matcher hrefMatcher = ALL_HREFS.matcher(html);
        while (hrefMatcher.find()) {
            String candidate = hrefMatcher.group(1).trim();
            String decoded = decode(candidate);
            if (!keyOf(decoded).equals(seriesKey)) continue;
            if (!seen.add(candidate)) continue;
            int[] se = parseSeasonEpisode(decoded);
            if (se[1] <= 0) continue;
            int season = se[0] > 0 ? se[0] : 1;
            String episodeKey = season + ":" + se[1];
            if (!seenEpisodeKeys.add(episodeKey)) continue;
            SeasonGroup.Builder builder = builders.get(season);
            if (builder == null) {
                builder = new SeasonGroup.Builder(season,
                        "الموسم " + season, seasonPosters.get(season));
                builders.put(season, builder);
            }
            String title = "الحلقة " + se[1];
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("source", "topcinemaa");
            builder.add(new CatalogItem(title,
                    seasonPosters.get(season) != null ? seasonPosters.get(season) : item.imageUrl,
                    candidate, "episode", season, se[1], meta,
                    0L, "", 0f, "", "", "", "tv", ID, candidate));
        }

        List<SeasonGroup> result = new ArrayList<>();
        for (SeasonGroup.Builder builder : builders.values()) {
            SeasonGroup group = builder.build();
            if (!group.episodes.isEmpty()) result.add(group);
        }
        result.sort(Comparator.comparingInt(g -> g.seasonNumber));
        return result;
    }

    /** Removes domain, /series/ prefix and the season/episode suffix to get a series key. */
    static String keyOf(String decodedUrl) {
        String p = decodedUrl == null ? "" : decodedUrl;
        p = p.replaceFirst("^https?://[^/]+/", "");
        p = p.replaceFirst("^series/", "");
        int i = p.indexOf("الموسم");
        if (i > 0) p = p.substring(0, i);
        p = p.replaceAll("[-/]+$", "");
        return p.trim();
    }

    /** Parses [season, episode] from a decoded TopCinemaa URL/title. */
    static int[] parseSeasonEpisode(String text) {
        int season = 0;
        int episode = 0;
        Matcher epMatcher = Pattern.compile("الحلقة-?\\s*(\\d+)").matcher(text);
        if (epMatcher.find()) {
            try {
                episode = Integer.parseInt(epMatcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        Matcher seasonMatcher = Pattern.compile("الموسم-?\\s*([^-\\d\\s]+|\\d+)").matcher(text);
        if (seasonMatcher.find()) {
            String value = seasonMatcher.group(1).trim();
            Integer ordinal = AR_SEASON.get(value);
            if (ordinal != null) {
                season = ordinal;
            } else {
                try {
                    season = Integer.parseInt(value);
                } catch (Exception ignored) {
                    // not a number
                }
            }
        }
        if (episode > 0 && season <= 0) season = 1;
        return new int[]{season, episode};
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return value;
        }
    }

    // ------------------------------------------------------------------ playback

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
        if (page == null || !page.startsWith(BASE)) return null;
        List<String> media = HtmlFetcher.findDirectMedia(HtmlFetcher.get(page));
        if (media.isEmpty()) {
            String embedScreen = page + (page.contains("?") ? "&" : "?") + "embedScreen=true";
            try {
                media = HtmlFetcher.findDirectMedia(HtmlFetcher.get(embedScreen));
            } catch (Exception ignored) {
                // Embed page not readable — give up (no bypass).
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
