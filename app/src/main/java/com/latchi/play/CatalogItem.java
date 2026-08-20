package com.latchi.play;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogItem implements Serializable {
    private static final long serialVersionUID = 2L;

    public final String title;
    public final String imageUrl;
    public final String pageUrl;
    public final String type;
    public final int seasonNumber;
    public final int episodeNumber;
    public final Map<String, String> metadata;

    public CatalogItem(String title, String imageUrl, String pageUrl, String type) {
        this(title, imageUrl, pageUrl, type, 0, 0, Collections.emptyMap());
    }

    public CatalogItem(String title, String imageUrl, String pageUrl, String type,
                       int seasonNumber, int episodeNumber, Map<String, String> metadata) {
        this.title = title == null ? "" : title;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.pageUrl = pageUrl == null ? "" : pageUrl;
        this.type = type == null ? "movie" : type;
        this.seasonNumber = Math.max(0, seasonNumber);
        this.episodeNumber = Math.max(0, episodeNumber);
        this.metadata = metadata == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
