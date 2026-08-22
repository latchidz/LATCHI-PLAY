package com.latchi.play;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of available content providers.
 * The provider chosen in Settings is tried first, then every configured provider,
 * then the always-available free ones — giving automatic failover.
 */
public final class ProviderRegistry {
    private ProviderRegistry() {
    }

    public static List<ContentProvider> ordered(Context context) {
        AppPrefs prefs = new AppPrefs(context);
        List<ContentProvider> all = new ArrayList<>();
        all.add(new ArchiveOrgProvider());
        all.add(new PeerTubeProvider(context));
        all.add(new TopCinemaaProvider());
        all.add(new EgyDeadProvider());
        all.add(new MyCimaProvider());
        all.add(new XtreamProvider(context));

        String preferred = prefs.getProviderId();
        List<ContentProvider> configured = new ArrayList<>();
        List<ContentProvider> other = new ArrayList<>();
        for (ContentProvider provider : all) {
            if (provider.isConfigured(context)) configured.add(provider);
            else other.add(provider);
        }

        List<ContentProvider> result = new ArrayList<>();
        ContentProvider firstChoice = null;
        for (ContentProvider provider : configured) {
            if (provider.id().equals(preferred)) {
                firstChoice = provider;
                break;
            }
        }
        if (firstChoice != null) {
            result.add(firstChoice);
            configured.remove(firstChoice);
        }
        result.addAll(configured);
        result.addAll(other);
        return result;
    }

    /** Providers that can be browsed inside the app (supportsCatalog). */
    public static List<ContentProvider> catalogProviders(Context context) {
        List<ContentProvider> result = new ArrayList<>();
        for (ContentProvider provider : ordered(context)) {
            if (provider.supportsCatalog()) result.add(provider);
        }
        return result;
    }

    public static ContentProvider byId(Context context, String id) {
        for (ContentProvider provider : ordered(context)) {
            if (provider.id().equals(id)) return provider;
        }
        return null;
    }
}
