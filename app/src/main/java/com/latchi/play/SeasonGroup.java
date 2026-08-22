package com.latchi.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A season with its episodes, normalized and numerically ordered. */
public final class SeasonGroup {
    public final int seasonNumber;
    public final String title;
    public final String posterUrl;
    public final List<CatalogItem> episodes;

    public SeasonGroup(int seasonNumber, String title, String posterUrl,
                       List<CatalogItem> episodes) {
        this.seasonNumber = Math.max(0, seasonNumber);
        this.title = title == null ? "" : title;
        this.posterUrl = posterUrl == null ? "" : posterUrl;
        this.episodes = episodes == null ? new ArrayList<>() : episodes;
    }

    public static final class Builder {
        private final int seasonNumber;
        private final String title;
        private final String posterUrl;
        private final List<CatalogItem> episodes = new ArrayList<>();

        public Builder(int seasonNumber, String title, String posterUrl) {
            this.seasonNumber = seasonNumber;
            this.title = title;
            this.posterUrl = posterUrl;
        }

        public Builder add(CatalogItem episode) {
            if (episode != null) episodes.add(episode);
            return this;
        }

        public SeasonGroup build() {
            episodes.sort((a, b) -> {
                int sa = Math.max(0, a.seasonNumber);
                int sb = Math.max(0, b.seasonNumber);
                if (sa != sb) return Integer.compare(sa, sb);
                int ea = a.episodeNumber > 0 ? a.episodeNumber : Integer.MAX_VALUE;
                int eb = b.episodeNumber > 0 ? b.episodeNumber : Integer.MAX_VALUE;
                return Integer.compare(ea, eb);
            });
            return new SeasonGroup(seasonNumber, title, posterUrl,
                    Collections.unmodifiableList(episodes));
        }
    }
}
