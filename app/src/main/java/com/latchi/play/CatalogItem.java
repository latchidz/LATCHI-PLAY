package com.latchi.play;

import java.io.Serializable;

public final class CatalogItem implements Serializable {
    public final String title;
    public final String imageUrl;
    public final String pageUrl;
    public final String type;

    public CatalogItem(String title, String imageUrl, String pageUrl, String type) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.pageUrl = pageUrl;
        this.type = type;
    }
}
