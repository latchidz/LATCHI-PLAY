package com.latchi.play;

import java.util.Collections;
import java.util.List;

/** One parsed catalog page plus the source-provided URL for the next page. */
public final class CatalogPage {
    public final List<CatalogItem> items;
    public final String nextPageUrl;

    public CatalogPage(List<CatalogItem> items, String nextPageUrl) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
        this.nextPageUrl = nextPageUrl;
    }
}
