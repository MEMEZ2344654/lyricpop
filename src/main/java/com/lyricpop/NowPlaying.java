package com.lyricpop;

/** Snapshot of what the OS says is playing. */
public record NowPlaying(String artist, String title, long positionMs, long durationMs, boolean playing) {
    public String key() { return (artist + "|" + title).toLowerCase(); }
}
