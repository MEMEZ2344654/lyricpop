package com.lyricpop;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Reads the system-wide "now playing" info (Spotify, browsers, etc).
 * Windows: one long-running PowerShell that prints a line ~4 times a second (fast, no restart cost).
 * macOS / Linux: polls osascript / playerctl.
 */
public final class NowPlayingProvider {
    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static volatile Process winProcess;

    public static void start(Consumer<NowPlaying> sink) {
        if (OS.contains("win")) startWindows(sink);
        else startPolling(sink);
    }

    // ---------------- Windows ----------------
    private static void startWindows(Consumer<NowPlaying> sink) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = winProcess;
            if (p != null) p.destroyForcibly();
        }));
        Thread t = new Thread(() -> {
            Path script;
            try {
                script = Files.createTempFile("lyricpop", ".ps1");
                Files.writeString(script, WIN_SCRIPT, StandardCharsets.UTF_8);
                script.toFile().deleteOnExit();
            } catch (IOException e) {
                return;
            }
            while (true) {
                Process pr = null;
                try {
                    pr = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
                    winProcess = pr;
                    final Process errProc = pr;
                    Thread drain = new Thread(() -> {
                        try { errProc.getErrorStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) { }
                    }, "LyricPop-PS-err");
                    drain.setDaemon(true);
                    drain.start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        try { sink.accept(parse(line)); } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (pr != null) pr.destroyForcibly();
                }
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
            }
        }, "LyricPop-Windows");
        t.setDaemon(true);
        t.start();
    }

    // ---------------- macOS / Linux ----------------
    private static void startPolling(Consumer<NowPlaying> sink) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LyricPop-Poll");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try {
                String out;
                if (OS.contains("mac")) out = run(new String[]{"osascript", "-e", MAC_SCRIPT});
                else out = run(new String[]{"playerctl", "metadata", "--format",
                        "{{artist}}\t{{title}}\t{{position}}\t{{mpris:length}}\t{{status}}"});
                sink.accept(parse(out));
            } catch (Exception e) {
                sink.accept(null);
            }
        }, 0, 800, TimeUnit.MILLISECONDS);
    }

    private static String run(String[] cmd) throws Exception {
        Process pr = new ProcessBuilder(cmd).start();
        String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!pr.waitFor(6, TimeUnit.SECONDS)) pr.destroyForcibly();
        return out;
    }

    private static NowPlaying parse(String out) {
        if (out == null || out.isBlank()) return null;
        String[] p = out.strip().split("\t");
        if (p.length < 5 || p[1].isBlank()) return null;
        double pos = Double.parseDouble(p[2].replace(',', '.'));
        double dur = Double.parseDouble(p[3].replace(',', '.'));
        long posMs, durMs;
        if (OS.contains("win")) { posMs = (long) pos; durMs = (long) dur; }
        else if (OS.contains("mac")) { posMs = (long) (pos * 1000); durMs = (long) dur; }
        else { posMs = (long) (pos / 1000); durMs = (long) (dur / 1000); }
        boolean playing = p[4].toLowerCase().contains("play");
        return new NowPlaying(p[0].strip(), p[1].strip(), posMs, durMs, playing);
    }

    private static final String MAC_SCRIPT =
        "if application \"Spotify\" is running then\n" +
        "tell application \"Spotify\" to return (artist of current track) & tab & (name of current track) & tab & (player position) & tab & (duration of current track) & tab & (player state as string)\n" +
        "else if application \"Music\" is running then\n" +
        "tell application \"Music\" to return (artist of current track) & tab & (name of current track) & tab & (player position) & tab & ((duration of current track) * 1000) & tab & (player state as string)\n" +
        "end if";

    // Windows media session reader. Position is corrected with LastUpdatedTime so it is accurate "right now".
    private static final String WIN_SCRIPT = String.join("\n",
        "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8",
        "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
        "$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
        "Function Await($op,$type){ $t=$asTask.MakeGenericMethod($type); $n=$t.Invoke($null,@($op)); $n.Wait(-1)|Out-Null; $n.Result }",
        "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null",
        "$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
        "while($true){",
        "  try {",
        "    $s=$mgr.GetCurrentSession()",
        "    if($s){",
        "      $p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
        "      $t=$s.GetTimelineProperties(); $i=$s.GetPlaybackInfo()",
        "      $st=\"$($i.PlaybackStatus)\"",
        "      $pos=[int64]$t.Position.TotalMilliseconds",
        "      if($st -eq 'Playing'){ $age=([DateTimeOffset]::UtcNow - $t.LastUpdatedTime).TotalMilliseconds; if($age -gt 0 -and $age -lt 600000){ $pos+=[int64]$age } }",
        "      $end=[int64]$t.EndTime.TotalMilliseconds",
        "      [Console]::Out.WriteLine(\"$($p.Artist)`t$($p.Title)`t$pos`t$end`t$st\")",
        "    } else { [Console]::Out.WriteLine('NONE') }",
        "  } catch { [Console]::Out.WriteLine('NONE') }",
        "  [Console]::Out.Flush()",
        "  Start-Sleep -Milliseconds 250",
        "}");
}
