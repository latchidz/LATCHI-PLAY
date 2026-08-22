package com.latchi.play;

import android.content.Context;

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
 * EgyDead TV provider (https://tv10.egydead.live).
 *
 * Catalog, details, seasons and episodes come from the public WordPress HTML.
 * Playback uses only direct media URLs literally present in the public HTML;
 * the site serves its stream behind a keyed anti-bot endpoint (data-ajax +
 * data-cp-host) that we deliberately do not reverse-engineer or bypass.
 */
public final class EgyDeadProvider implements ContentProvider {
    public static final String ID = "egydead";
    private static final String BASE = "https://tv10.egydead.live/";

    private static final Pattern CARD = Pattern.compile(
            "<a[^>]+href=\"(https://tv10\\.egydead\\.live/[^\"]+)\"[^>]*title=\"([^\"]*)\"[^>]*>" +
                    "(?:(?!</a>).)*?<img[^>]+src=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SEASON_LINK = Pattern.compile(
            "href=\"(https://tv10\\.egydead\\.live/season/([^\"]+)/)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_LINK = Pattern.compile(
            "href=\"(https://tv10\\.egydead\\.live/episode/([^\"]+)/)\"\\s+title=\"([^\"]*)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_SLUG = Pattern.compile(
            "^(.*?)(?:-s(\\d+))?e(\\d+)(?:-[a-z0-9\\-]*)?$", Pattern.CASE_INSENSITIVE);

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

            String type = classify(title, link);
            if (!typeFilter.isEmpty() && !type.equals(typeFilter)) continue;

            int[] se = parseSeasonEpisode(title, link);
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("source", "egydead");
            items.add(new CatalogItem(title, image, link, type, se[0], se[1], meta,
                    0L, "", 0f, "", "", "", type, ID, link));
        }
        return items;
    }

    private String classify(String title, String link) {
        String lower = link.toLowerCase(Locale.US) + " " + title;
        if (lower.contains("/episode/") || lower.contains("/season/") || lower.contains("/serie/")) {
            return "series";
        }
        if (lower.contains("%d9%81%d9%8a%d9%84%d9%85") || lower.contains("فيلم") ||
                lower.contains("1080p") || lower.contains("bluray") || lower.contains("web-dl")) {
            return "movie";
        }
        return "series";
    }

    private int[] parseSeasonEpisode(String title, String link) {
        int season = 0;
        int episode = 0;
        Matcher seasonMatcher = Pattern.compile("الموسم[^\\d]{0,6}(\\d+)").matcher(title);
        if (seasonMatcher.find()) {
            try {
                season = Integer.parseInt(seasonMatcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        Matcher episodeMatcher = Pattern.compile("الحلقة[^\\d]{0,6}(\\d+)").matcher(title);
        if (episodeMatcher.find()) {
            try {
                episode = Integer.parseInt(episodeMatcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        if (season <= 0 || episode <= 0) {
            Matcher code = Pattern.compile("s(\\d+)e(\\d+)").matcher(link.toLowerCase(Locale.US));
            if (code.find()) {
                season = Integer.parseInt(code.group(1));
                episode = Integer.parseInt(code.group(2));
            }
        }
        if (episode > 0 && season <= 0) season = 1;
        return new int[]{season, episode};
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
        if (title.isEmpty()) title = item.title;
        String description = SiteMeta.og(html, "og:description");
        if (description.isEmpty()) description = SiteMeta.meta(html, "twitter:description");
        String poster = SiteMeta.og(html, "og:image");
        String year = SiteMeta.year(title);
        if (year.isEmpty()) year = SiteMeta.year(description);

        String genres = "";
        Matcher genreMatcher = Pattern.compile("النوع\\s*:?\\s*([^<]{2,120})").matcher(html);
        if (genreMatcher.find()) {
            genres = SiteMeta.genres(genreMatcher.group(1));
        }
        if (genres.isEmpty()) genres = SiteMeta.genres(title + " " + description);

        List<String> cast = SiteMeta.cast(html);
        if (cast.isEmpty()) cast = SiteMeta.cast(description);

        return new MediaDetail(ID, page, item.type, title, "", poster, poster,
                description, year, 0f, 0, genres, "", "", 0, cast, "", "");
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

        // Collect season pages referenced on the item page.
        List<String> seasonUrls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher seasonMatcher = SEASON_LINK.matcher(html);
        while (seasonMatcher.find()) {
            String url = seasonMatcher.group(1);
            if (seen.add(url)) seasonUrls.add(url);
        }
        seasonUrls.sort(Comparator.naturalOrder());

        // Fallback: derive the series base from the current episode slug.
        String fallbackBase = seriesBase(page);

        Map<Integer, SeasonGroup.Builder> builders = new LinkedHashMap<>();

        if (!seasonUrls.isEmpty()) {
            for (String seasonUrl : seasonUrls) {
                int season = seasonNumberFromSlug(seasonUrl);
                if (season <= 0) continue;
                String seasonHtml = HtmlFetcher.get(seasonUrl);
                String seasonPoster = SiteMeta.og(seasonHtml, "og:image");
                Matcher epMatcher = EPISODE_LINK.matcher(seasonHtml);
                SeasonGroup.Builder builder = builders.get(season);
                if (builder == null) {
                    builder = new SeasonGroup.Builder(season, "الموسم " + season, seasonPoster);
                    builders.put(season, builder);
                }
                while (epMatcher.find()) {
                    addEpisode(builder, epMatcher.group(1), epMatcher.group(3),
                            seasonPoster.isEmpty() ? item.imageUrl : seasonPoster);
                }
            }
        } else if (fallbackBase != null) {
            // Sibling episodes with the same base slug, grouped by season.
            Matcher epMatcher = EPISODE_LINK.matcher(html);
            while (epMatcher.find()) {
                String slug = epMatcher.group(2);
                if (slug == null || !slug.startsWith(fallbackBase)) continue;
                int[] se = seasonEpisodeFromSlug(slug);
                if (se[1] <= 0) continue;
                SeasonGroup.Builder builder = builders.get(se[0]);
                if (builder == null) {
                    builder = new SeasonGroup.Builder(se[0], "الموسم " + se[0], item.imageUrl);
                    builders.put(se[0], builder);
                }
                addEpisode(builder, epMatcher.group(1), epMatcher.group(3), item.imageUrl);
            }
        }

        List<SeasonGroup> result = new ArrayList<>();
        for (SeasonGroup.Builder builder : builders.values()) {
            SeasonGroup group = builder.build();
            if (!group.episodes.isEmpty()) result.add(group);
        }
        result.sort(Comparator.comparingInt(g -> g.seasonNumber));
        return result;
    }

    private void addEpisode(SeasonGroup.Builder builder, String url, String title, String thumb) {
        if (url == null) return;
        int[] se = seasonEpisodeFromSlug(slugOf(url));
        if (se[1] <= 0) return;
        String display = (title != null && !title.trim().isEmpty())
                ? title.trim() : "الحلقة " + se[1];
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("source", "egydead");
        builder.add(new CatalogItem(display,
                thumb == null ? "" : thumb,
                url, "episode", se[0], se[1], meta,
                0L, "", 0f, "", "", "", "tv", ID, url));
    }

    private static String slugOf(String url) {
        String trimmed = url.replaceAll("/+$", "");
        int i = trimmed.lastIndexOf('/');
        return i >= 0 ? trimmed.substring(i + 1) : trimmed;
    }

    private static int[] seasonEpisodeFromSlug(String slug) {
        if (slug == null) return new int[]{0, 0};
        Matcher matcher = EPISODE_SLUG.matcher(slug);
        if (!matcher.matches()) return new int[]{0, 0};
        try {
            int season = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
            int episode = Integer.parseInt(matcher.group(3));
            return new int[]{season, episode};
        } catch (Exception error) {
            return new int[]{0, 0};
        }
    }

    private static int seasonNumberFromSlug(String url) {
        String slug = slugOf(url);
        Matcher matcher = Pattern.compile("-s(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(slug);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                // not a number
            }
        }
        return 0;
    }

    private static String seriesBase(String url) {
        String slug = slugOf(url);
        Matcher matcher = EPISODE_SLUG.matcher(slug);
        if (matcher.matches()) return matcher.group(1);
        return null;
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
