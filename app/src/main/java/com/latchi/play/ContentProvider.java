package com.latchi.play;

import android.content.Context;

import java.util.List;

/**
 * A content source. Providers expose catalog browsing (when supported) and resolve
 * a catalog item into a direct media URL (mp4 / m3u8 / mpd) that Media3 can play.
 *
 * Implementations only use authorized APIs, public web pages the user pointed at,
 * or servers the user owns. They never extract hidden links, execute scripts to
 * reveal embeds, or bypass DRM/Cloudflare/CAPTCHA/access controls.
 */
public interface ContentProvider {

    String id();

    String label(Context context);

    boolean isConfigured(Context context);

    /** Resolves a direct playback source for the given item. */
    void resolve(CatalogItem item, Callback callback);

    // ------------------------------------------------------------------ catalog
    // Providers that can be browsed inside the app implement these; others
    // return "not supported" via the defaults.

    default boolean supportsCatalog() {
        return false;
    }

    default void home(int page, CatalogCallback callback) {
        callback.onError();
    }

    default void movies(int page, CatalogCallback callback) {
        callback.onError();
    }

    default void series(int page, CatalogCallback callback) {
        callback.onError();
    }

    default void search(String query, int page, CatalogCallback callback) {
        callback.onError();
    }

    /** Rich details for a movie/series (only fields the source provides). */
    default void details(CatalogItem item, DetailsCallback callback) {
        callback.onError();
    }

    /** Seasons + episodes for a series, normalized and numerically ordered. */
    default void episodes(CatalogItem item, EpisodesCallback callback) {
        callback.onError();
    }

    interface Callback {
        void onResolved(PlaybackSource source, String providerLabel);

        void onError();
    }

    interface CatalogCallback {
        void onSuccess(List<CatalogItem> items, boolean hasMore);

        void onError();
    }

    interface DetailsCallback {
        void onSuccess(MediaDetail detail);

        void onError();
    }

    interface EpisodesCallback {
        void onSuccess(List<SeasonGroup> seasons);

        void onError();
    }
}
