package com.latchi.play;

public final class HistoryEntry {
    public final CatalogItem item;
    public final long positionMs;
    public final long durationMs;
    public final long lastPlayedAt;

    public HistoryEntry(CatalogItem item, long positionMs, long durationMs, long lastPlayedAt) {
        this.item = item;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.lastPlayedAt = lastPlayedAt;
    }

    public int progressPercent() {
        if (durationMs <= 0) return 0;
        return (int) Math.max(0, Math.min(100, (positionMs * 100L) / durationMs));
    }
}
