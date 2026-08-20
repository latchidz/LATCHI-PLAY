package com.latchi.play;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A validated direct media source that Media3 can play without opening a web page. */
public final class PlaybackSource {
    public final String url;
    public final String type;
    public final Map<String, String> headers;
    public final Map<String, String> metadata;

    public PlaybackSource(String url, String type, Map<String, String> headers,
                          Map<String, String> metadata) {
        this.url = url;
        this.type = type;
        this.headers = headers == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.metadata = metadata == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
