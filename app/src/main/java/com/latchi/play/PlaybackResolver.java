package com.latchi.play;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates playback resolution across providers:
 * tries each provider in order with a bounded timeout, validates the resolved
 * source, and reports the best result or a clear "unavailable" state.
 * No infinite retries, no bypass attempts.
 */
public final class PlaybackResolver {
    public interface Callback {
        void onResolved(PlaybackSource source, String providerLabel, int attempts);

        void onUnavailable(int attempts);
    }

    private static final long PROVIDER_TIMEOUT_MS = 14_000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<ContentProvider> providers;
    private Callback callback;
    private CatalogItem item;
    private int index;
    private int attempts;
    private boolean cancelled;

    public PlaybackResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public void resolve(CatalogItem item, Callback callback) {
        cancel();
        this.item = item;
        this.callback = callback;
        this.providers = ProviderRegistry.ordered(context);
        this.index = 0;
        this.attempts = 0;
        this.cancelled = false;
        tryNext();
    }

    private void tryNext() {
        if (cancelled || callback == null) return;
        if (index >= providers.size()) {
            notifyUnavailable();
            return;
        }
        final int myIndex = index;
        final ContentProvider provider = providers.get(index);
        index++;
        attempts++;

        final Runnable timeout = () -> {
            if (!cancelled && myIndex == index - 1) tryNext();
        };
        handler.postDelayed(timeout, PROVIDER_TIMEOUT_MS);

        provider.resolve(item, new ContentProvider.Callback() {
            @Override
            public void onResolved(PlaybackSource source, String providerLabel) {
                if (cancelled) return;
                handler.removeCallbacks(timeout);
                if (source == null || source.url == null || source.url.isEmpty()) {
                    tryNext();
                    return;
                }
                // Providers already validate; keep a final sanity check for http(s) only.
                String url = source.url.trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    tryNext();
                    return;
                }
                if (callback != null) callback.onResolved(source, providerLabel, attempts);
            }

            @Override
            public void onError() {
                if (cancelled) return;
                handler.removeCallbacks(timeout);
                tryNext();
            }
        });
    }

    private void notifyUnavailable() {
        if (callback != null) callback.onUnavailable(attempts);
    }

    public void cancel() {
        cancelled = true;
        callback = null;
        handler.removeCallbacksAndMessages(null);
    }
}
