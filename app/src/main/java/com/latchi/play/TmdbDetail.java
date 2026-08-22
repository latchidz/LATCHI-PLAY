package com.latchi.play;

import java.util.Collections;
import java.util.List;

/** Rich TMDB detail payload (movie or TV series) used by the detail screen. */
public final class TmdbDetail {
    public final long tmdbId;
    public final String mediaType;    // movie | tv
    public final String title;
    public final String overview;
    public final float rating;
    public final String year;
    public final String genres;
    public final String posterUrl;
    public final String backdropUrl;
    public final int runtimeMinutes;
    public final List<TmdbSeason> seasons;

    public TmdbDetail(long tmdbId, String mediaType, String title, String overview,
                      float rating, String year, String genres, String posterUrl,
                      String backdropUrl, int runtimeMinutes, List<TmdbSeason> seasons) {
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.title = title == null ? "" : title;
        this.overview = overview == null ? "" : overview;
        this.rating = rating;
        this.year = year == null ? "" : year;
        this.genres = genres == null ? "" : genres;
        this.posterUrl = posterUrl == null ? "" : posterUrl;
        this.backdropUrl = backdropUrl == null ? "" : backdropUrl;
        this.runtimeMinutes = runtimeMinutes;
        this.seasons = seasons == null ? Collections.emptyList() : seasons;
    }

    public boolean isTv() {
        return "tv".equals(mediaType);
    }

    /** A single season of a TV series. */
    public static final class TmdbSeason {
        public final int seasonNumber;
        public final String name;
        public final int episodeCount;
        public final String posterPath;

        public TmdbSeason(int seasonNumber, String name, int episodeCount, String posterPath) {
            this.seasonNumber = seasonNumber;
            this.name = name == null ? "" : name;
            this.episodeCount = episodeCount;
            this.posterPath = posterPath == null ? "" : posterPath;
        }
    }
}
