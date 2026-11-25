package com.lyricpop;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** Fetches time-synced lyrics (LRC) from LRCLIB for any song, and splits lines into timed words. */
public final class Lyrics {
    public record Word(String text, long startMs, long endMs) {}
    public record Line(long startMs, long endMs, String text, List<Word> words) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)");

    public final List<Line> lines;
    public Lyrics(List<Line> lines) { this.lines = lines; }

    /** Blocking; call off the render thread. Returns null if nothing found. */
    public static Lyrics fetch(NowPlaying np) {
        String title = clean(np.title()), artist = np.artist().split("[,&;]")[0].strip();
        try {
            String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                    + (np.durationMs() > 0 ? "&duration=" + np.durationMs() / 1000 : "");
            JsonObject o = getJson(url) instanceof JsonObject j ? j : null;
            if (o != null && hasSynced(o)) return parse(o.get("syncedLyrics").getAsString(), np.durationMs());
            // fallback: fuzzy search
            JsonElement arr = getJson("https://lrclib.net/api/search?q=" + enc(artist + " " + title));
            if (arr instanceof JsonArray a) for (JsonElement e : a)
                if (e.getAsJsonObject() instanceof JsonObject j && hasSynced(j))
                    return parse(j.get("syncedLyrics").getAsString(), np.durationMs());
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean hasSynced(JsonObject o) {
        return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() && !o.get("syncedLyrics").getAsString().isBlank();
    }

    private static JsonElement getJson(String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "LyricPop Minecraft mod (fabric)").build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return res.statusCode() == 200 ? JsonParser.parseString(res.body()) : null;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** "Song - Remastered 2011", "Song (feat. X)" -> "Song" */
    static String clean(String t) {
        return t.replaceAll("(?i)\\s*[\\(\\[][^)\\]]*(feat|ft\\.|remaster|live|version|edit)[^)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s+-\\s+(remaster|live|single|radio|bonus).*$", "").strip();
    }

    static Lyrics parse(String lrc, long durationMs) {
        List<long[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            Matcher m = LRC.matcher(raw.strip());
            if (!m.matches()) continue;
            long t = (long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000);
            times.add(new long[]{t});
            texts.add(m.group(3).strip());
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            long start = times.get(i)[0];
            long end = i + 1 < texts.size() ? times.get(i + 1)[0] : (durationMs > start ? durationMs : start + 4000);
            out.add(new Line(start, end, texts.get(i), splitWords(texts.get(i), start, end)));
        }
        return new Lyrics(out);
    }

    /** LRCLIB is line-synced, so spread words across the line by character count (sung portion capped at ~6s). */
    private static List<Word> splitWords(String text, long start, long end) {
        if (text.isBlank()) return List.of();
        String[] ws = text.split("\\s+");
        long span = Math.min(end - start, 6000);
        span = Math.max(span - 250, 400);
        int total = 0; for (String w : ws) total += w.length() + 1;
        List<Word> out = new ArrayList<>();
        long cursor = start; 
        for (String w : ws) {
            long len = Math.max(120, span * (w.length() + 1) / total);
            out.add(new Word(w, cursor, cursor + len));
            cursor += len;
        }
        return out;
    }

    /** Index of the line active at posMs, or -1. */
    public int lineAt(long posMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs() <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }
}
