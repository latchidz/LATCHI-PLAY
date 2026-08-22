package com.latchi.play;

import android.content.Context;

/**
 * A playback source provider. A provider turns a catalog item into a direct media
 * URL (mp4 / m3u8 / mpd) that Media3 can play natively.
 * Implementations only use authorized APIs or content the user owns.
 */
public interface ContentProvider {
    String id();

    String label(Context context);

    boolean isConfigured(Context context);

    /** Resolves a direct playback source for the given item. Must call the callback on the calling thread. */
    void resolve(CatalogItem item, Callback callback);

    interface Callback {
        void onResolved(PlaybackSource source, String providerLabel);

        void onError();
    }
}
