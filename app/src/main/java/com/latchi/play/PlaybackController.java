package com.latchi.play;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.Collections;
import java.util.Map;

/** Owns Media3 lifecycle and resume position without exposing source details to the UI. */
public final class PlaybackController {
    public interface Callback {
        void onBuffering();
        void onReady();
        void onEnded();
        void onError();
    }

    private static final String PREFS = "playback_positions";
    private static final long MIN_RESUME_POSITION_MS = 5_000;
    private static final long COMPLETED_MARGIN_MS = 15_000;

    private final Context context;
    private final PlayerView playerView;
    private final SharedPreferences positions;
    private ExoPlayer player;
    private Callback callback;
    private String contentKey;

    public PlaybackController(Context context, PlayerView playerView) {
        this.context = context.getApplicationContext();
        this.playerView = playerView;
        this.positions = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void prepare(String contentKey, String url, String type, Map<String, String> headers,
                        Callback callback) {
        prepare(contentKey, url, type, headers, true, callback);
    }

    public void prepare(String contentKey, String url, String type, Map<String, String> headers,
                        boolean resumePosition, Callback callback) {
        releasePlayer(false);
        this.callback = callback;
        this.contentKey = contentKey;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("LATCHI-PLAY/" + BuildConfig.VERSION_NAME)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(18_000)
                .setDefaultRequestProperties(headers == null ? Collections.emptyMap() : headers);

        player = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setShowRewindButton(true);
        playerView.setShowFastForwardButton(true);
        playerView.setShowSubtitleButton(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                if (isLoading && PlaybackController.this.callback != null) {
                    PlaybackController.this.callback.onBuffering();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (PlaybackController.this.callback == null) return;
                if (playbackState == Player.STATE_READY) PlaybackController.this.callback.onReady();
                else if (playbackState == Player.STATE_ENDED) {
                    clearPosition();
                    PlaybackController.this.callback.onEnded();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (PlaybackController.this.callback != null) PlaybackController.this.callback.onError();
            }
        });

        MediaItem.Builder media = new MediaItem.Builder().setUri(url);
        String mimeType = mimeType(type, url);
        if (mimeType != null) media.setMimeType(mimeType);
        player.setMediaItem(media.build());

        if (resumePosition) {
            long savedPosition = positions.getLong(contentKey, 0L);
            if (savedPosition >= MIN_RESUME_POSITION_MS) player.seekTo(savedPosition);
        }
        player.prepare();
        player.play();
    }

    public void resume() {
        if (player != null) player.play();
    }

    public void pause() {
        if (player != null) {
            savePosition();
            player.pause();
        }
    }

    public boolean isActive() {
        return player != null;
    }

    public long getCurrentPosition() {
        return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
    }

    public long getDuration() {
        return player == null ? 0L : Math.max(0L, player.getDuration());
    }

    public void release() {
        releasePlayer(true);
    }

    private void releasePlayer(boolean save) {
        if (player == null) return;
        if (save) savePosition();
        playerView.setPlayer(null);
        player.release();
        player = null;
        callback = null;
    }

    private void savePosition() {
        if (player == null || contentKey == null) return;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (position < MIN_RESUME_POSITION_MS ||
                (duration > 0 && position >= duration - COMPLETED_MARGIN_MS)) {
            clearPosition();
        } else {
            positions.edit().putLong(contentKey, position).apply();
        }
    }

    private void clearPosition() {
        if (contentKey != null) positions.edit().remove(contentKey).apply();
    }

    private String mimeType(String type, String url) {
        String normalizedType = type == null ? "" : type.toLowerCase();
        String normalizedUrl = url == null ? "" : url.toLowerCase();
        if (normalizedType.equals("hls") || normalizedUrl.contains(".m3u8")) {
            return MimeTypes.APPLICATION_M3U8;
        }
        if (normalizedType.equals("dash") || normalizedUrl.contains(".mpd")) {
            return MimeTypes.APPLICATION_MPD;
        }
        if (normalizedType.equals("mp4") || normalizedUrl.contains(".mp4")) {
            return MimeTypes.VIDEO_MP4;
        }
        return null;
    }
}
