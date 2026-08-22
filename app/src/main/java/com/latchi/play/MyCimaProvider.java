package com.latchi.play;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MyCima provider (https://mycima.cafe).
 *
 * The site is protected by an active Cloudflare challenge ("Just a moment...")
 * and serves its streams through protected embed players. The app does not
 * bypass challenges or extract hidden links, so this provider reports
 * "unavailable" immediately and the resolver fails over to the next source.
 */
public final class MyCimaProvider implements ContentProvider {
    public static final String ID = "mycima";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label(Context context) {
        return "MyCima (مقفول)";
    }

    @Override
    public boolean isConfigured(Context context) {
        return true;
    }

    @Override
    public boolean supportsCatalog() {
        return false;
    }

    @Override
    public void resolve(CatalogItem item, Callback callback) {
        // Cloudflare challenge: not reachable without bypassing protection.
        callback.onError();
    }

    public void destroy() {
        destroyed.set(true);
        executor.shutdownNow();
    }
}
