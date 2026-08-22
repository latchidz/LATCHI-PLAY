package com.latchi.play;

import java.util.Collections;
import java.util.List;

/**
 * Provider-agnostic rich details for a movie or series.
 * Only fields the source actually provides are filled; empty = not available.
 */
public final class MediaDetail {
    public final String providerId;
    public final String contentId;
    public final String type;              // movie | series
    public final String title;
    public final String originalTitle;
    public final String posterUrl;
    public final String backdropUrl;
    public final String description;
    public final String year;
    public final float rating;             // 0 = none
    public final int ratingCount;          // 0 = none
    public final String genres;            // "أكشن • جريمة" or empty
    public final String country;
    public final String language;
    public final int durationMinutes;      // 0 = none
    public final List<String> cast;        // empty = none
    public final String director;
    public final String trailerUrl;        // empty = none

    public MediaDetail(String providerId, String contentId, String type, String title,
                       String originalTitle, String posterUrl, String backdropUrl,
                       String description, String year, float rating, int ratingCount,
                       String genres, String country, String language, int durationMinutes,
                       List<String> cast, String director, String trailerUrl) {
        this.providerId = providerId == null ? "" : providerId;
        this.contentId = contentId == null ? "" : contentId;
        this.type = (type == null || type.isEmpty()) ? "movie" : type;
        this.title = title == null ? "" : title;
        this.originalTitle = originalTitle == null ? "" : originalTitle;
        this.posterUrl = posterUrl == null ? "" : posterUrl;
        this.backdropUrl = backdropUrl == null ? "" : backdropUrl;
        this.description = description == null ? "" : description;
        this.year = year == null ? "" : year;
        this.rating = rating;
        this.ratingCount = ratingCount;
        this.genres = genres == null ? "" : genres;
        this.country = country == null ? "" : country;
        this.language = language == null ? "" : language;
        this.durationMinutes = durationMinutes;
        this.cast = cast == null ? Collections.emptyList() : cast;
        this.director = director == null ? "" : director;
        this.trailerUrl = trailerUrl == null ? "" : trailerUrl;
    }
}
