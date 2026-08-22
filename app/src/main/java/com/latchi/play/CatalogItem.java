package com.latchi.play;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified catalog model used by TMDB discovery, web-site providers, favorites,
 * history and the watch screen.
 *
 * {@code pageUrl} is the stable unique key per provider ("tmdb:movie:123",
 * "https://topcinemaa.co/...", ...). {@code providerId} + {@code contentId}
 * identify the item without relying on the URL.
 */
public final class CatalogItem implements Serializable {
    private static final long serialVersionUID = 4L;

    public final String title;
    public final String imageUrl;
    public final String pageUrl;
    public final String type;          // movie | series | episode
    public final int seasonNumber;
    public final int episodeNumber;
    public final Map<String, String> metadata;

    public final long tmdbId;
    public final String mediaType;     // movie | tv
    public final String overview;
    public final float rating;
    public final String year;
    public final String backdropUrl;
    public final String genres;

    public final String providerId;    // which ContentProvider owns this item
    public final String contentId;     // provider-internal content id

    public CatalogItem(String title, String imageUrl, String pageUrl, String type) {
        this(title, imageUrl, pageUrl, type, 0, 0, Collections.emptyMap());
    }

    public CatalogItem(String title, String imageUrl, String pageUrl, String type,
                       int seasonNumber, int episodeNumber, Map<String, String> metadata) {
        this(title, imageUrl, pageUrl, type, seasonNumber, episodeNumber, metadata,
                0L, "", 0f, "", "", "", type);
    }

    public CatalogItem(String title, String imageUrl, String pageUrl, String type,
                       int seasonNumber, int episodeNumber, Map<String, String> metadata,
                       long tmdbId, String overview, float rating, String year,
                       String backdropUrl, String genres, String mediaType) {
        this(title, imageUrl, pageUrl, type, seasonNumber, episodeNumber, metadata,
                tmdbId, overview, rating, year, backdropUrl, genres, mediaType, "", "");
    }

    public CatalogItem(String title, String imageUrl, String pageUrl, String type,
                       int seasonNumber, int episodeNumber, Map<String, String> metadata,
                       long tmdbId, String overview, float rating, String year,
                       String backdropUrl, String genres, String mediaType,
                       String providerId, String contentId) {
        this.title = title == null ? "" : title;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.pageUrl = pageUrl == null ? "" : pageUrl;
        this.type = type == null ? "movie" : type;
        this.seasonNumber = Math.max(0, seasonNumber);
        this.episodeNumber = Math.max(0, episodeNumber);
        this.metadata = metadata == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.tmdbId = tmdbId;
        this.overview = overview == null ? "" : overview;
        this.rating = rating;
        this.year = year == null ? "" : year;
        this.backdropUrl = backdropUrl == null ? "" : backdropUrl;
        this.genres = genres == null ? "" : genres;
        this.mediaType = (mediaType == null || mediaType.isEmpty()) ? type : mediaType;
        this.providerId = providerId == null ? "" : providerId;
        this.contentId = contentId == null ? "" : contentId;
    }

    /** Builds a movie/series item straight from a TMDB discovery result. */
    public static CatalogItem fromTmdb(long id, String mediaType, String title,
                                       String posterPath, String backdropPath,
                                       float rating, String releaseYear, String overview) {
        boolean tv = "tv".equals(mediaType);
        String type = tv ? "series" : "movie";
        return new CatalogItem(title,
                TmdbClient.posterUrl(posterPath, 500),
                "tmdb:" + mediaType + ":" + id,
                type, 0, 0, Collections.emptyMap(),
                id, overview, rating, releaseYear,
                TmdbClient.backdropUrl(backdropPath),
                "", mediaType, "tmdb", String.valueOf(id));
    }

    /** Copies this item but attaches a provider identity (used by web-site providers). */
    public CatalogItem withProvider(String provider, String contentId) {
        return new CatalogItem(title, imageUrl, pageUrl, type, seasonNumber, episodeNumber,
                metadata, tmdbId, overview, rating, year, backdropUrl, genres, mediaType,
                provider, contentId);
    }

    /** Recovers the TMDB id from a pageUrl like "tmdb:movie:123" (legacy saved items). */
    public static long tmdbIdFromPageUrl(String pageUrl) {
        if (pageUrl == null) return 0L;
        try {
            String[] parts = pageUrl.split(":");
            if (parts.length >= 3 && "tmdb".equals(parts[0])) {
                String idPart = parts[2];
                int s = idPart.indexOf("s");
                if (s > 0) idPart = idPart.substring(0, s);
                return Long.parseLong(idPart);
            }
        } catch (Exception ignored) {
            // Not a TMDB pageUrl.
        }
        return 0L;
    }

    /** Recovers "movie" or "tv" from a pageUrl like "tmdb:movie:123" or "tmdb:tv:123:s1e2". */
    public static String mediaTypeFromPageUrl(String pageUrl) {
        if (pageUrl != null && pageUrl.contains(":tv:")) return "tv";
        return "movie";
    }
}
