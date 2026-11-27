package com.lyricpop;

import java.util.concurrent.*;

/** Receives now-playing updates, fetches lyrics, and keeps a smooth playback clock. */
public final class LyricsManager {
    public static volatile NowPlaying now;
    public static volatile Lyrics lyrics;
    public static volatile boolean loading;
    private static volatile long anchorPosMs, anchorNanos;
    private static volatile String loadedKey = "";
    private static final ExecutorService FETCH = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LyricPop-Fetch");
        t.setDaemon(true);
        return t;
    });

    public static void start() {
        NowPlayingProvider.start(LyricsManager::onUpdate);
    }

    private static void onUpdate(NowPlaying np) {
        if (np == null) {
            now = null;
            lyrics = null;
            loadedKey = "";
            return;
        }
        boolean newTrack = !np.key().equals(loadedKey);
        NowPlaying prev = now;
        boolean stateChange = prev == null || prev.playing() != np.playing();
        long expected = rawPosMs();
        if (newTrack || stateChange || !np.playing() || Math.abs(expected - np.positionMs()) > 350) {
            anchorPosMs = np.positionMs();
            anchorNanos = System.nanoTime();
        }
        now = np;
        if (newTrack) {
            loadedKey = np.key();
            lyrics = null;
            loading = true;
            final String key = np.key();
            FETCH.submit(() -> {
                Lyrics l = Lyrics.fetch(np);
                if (key.equals(loadedKey)) {
                    lyrics = l;
                    loading = false;
                }
            });
        }
    }

    /** Playback position without the user's sync offset. */
    public static long rawPosMs() {
        NowPlaying np = now;
        if (np == null) return 0;
        long pos = np.playing() ? anchorPosMs + (System.nanoTime() - anchorNanos) / 1_000_000L : anchorPosMs;
        return np.durationMs() > 0 ? Math.min(pos, np.durationMs()) : pos;
    }

    /** Position used for lyric timing (includes the sync offset setting). */
    public static long currentPosMs() {
        return rawPosMs() + Config.syncOffset;
    }
}
