package com.latchi.play;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public server entry discovered on the content page; it is not itself assumed to be media. */
public final class ServerInfo {
    public final String name;
    public final String pageUrl;
    public final int priority;
    public final Map<String, String> metadata;

    public ServerInfo(String name, String pageUrl, int priority, Map<String, String> metadata) {
        this.name = name;
        this.pageUrl = pageUrl;
        this.priority = priority;
        this.metadata = metadata == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
