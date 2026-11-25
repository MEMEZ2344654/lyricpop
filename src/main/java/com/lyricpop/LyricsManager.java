package com.lyricpop;

import java.util.concurrent.*;

/** Polls the OS for the current song, fetches lyrics, and keeps a smooth playback clock. */
public final class LyricsManager {
    public static volatile NowPlaying now;
    public static volatile Lyrics lyrics;
    public static volatile boolean loading;
    private static volatile long polledPosMs, polledAtNanos;
    private static String loadedKey = "";
    private static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "LyricPop"); t.setDaemon(true); return t;
    });

    public static void start() {
        EXEC.scheduleWithFixedDelay(LyricsManager::tick, 0, 1500, TimeUnit.MILLISECONDS);
    }

    private static void tick() {
        NowPlaying np = NowPlayingProvider.poll();
        if (np == null) { now = null; lyrics = null; loadedKey = ""; return; }
        long expected = currentPosMs();
        // Resync only when we drift (seek / pause) so the clock stays smooth.
        if (!np.key().equals(loadedKey) || Math.abs(expected - np.positionMs()) > 1200 || !np.playing()) {
            polledPosMs = np.positionMs();
            polledAtNanos = System.nanoTime();
        }
        now = np;
        if (!np.key().equals(loadedKey)) {
            loadedKey = np.key();
            lyrics = null; loading = true;
            EXEC.submit(() -> {
                Lyrics l = Lyrics.fetch(np);
                if (np.key().equals(loadedKey)) { lyrics = l; loading = false; }
            });
        }
    }

    public static long currentPosMs() {
        NowPlaying np = now;
        if (np == null) return 0;
        long pos = np.playing() ? polledPosMs + (System.nanoTime() - polledAtNanos) / 1_000_000 : polledPosMs;
        return np.durationMs() > 0 ? Math.min(pos, np.durationMs()) : pos;
    }
}
