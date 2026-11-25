package com.lyricpop;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * Reads the system-wide "now playing" info so it works with Spotify, Apple Music,
 * YouTube in a browser, etc. Uses a small OS-specific helper per platform.
 */
public final class NowPlayingProvider {
    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static Path winScript;

    public static NowPlaying poll() {
        try {
            String out;
            if (OS.contains("win")) out = run(windowsCmd());
            else if (OS.contains("mac")) out = run(new String[]{"osascript", "-e", MAC_SCRIPT});
            else out = run(new String[]{"playerctl", "metadata", "--format",
                    "{{artist}}\t{{title}}\t{{position}}\t{{mpris:length}}\t{{status}}"});
            return parse(out);
        } catch (Exception e) {
            return null;
        }
    }

    private static NowPlaying parse(String out) {
        if (out == null || out.isBlank()) return null;
        String[] p = out.strip().split("\t");
        if (p.length < 5 || p[1].isBlank()) return null;
        double pos = Double.parseDouble(p[2].replace(',', '.'));
        double dur = Double.parseDouble(p[3].replace(',', '.'));
        long posMs, durMs;
        if (OS.contains("win")) { posMs = (long) pos; durMs = (long) dur; }          // already ms
        else if (OS.contains("mac")) { posMs = (long) (pos * 1000); durMs = (long) dur; } // sec / ms
        else { posMs = (long) (pos / 1000); durMs = (long) (dur / 1000); }              // microseconds
        boolean playing = p[4].toLowerCase().contains("play");
        return new NowPlaying(p[0].strip(), p[1].strip(), posMs, durMs, playing);
    }

    private static String run(String[] cmd) throws Exception {
        Process pr = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!pr.waitFor(6, TimeUnit.SECONDS)) pr.destroyForcibly();
        return out;
    }

    private static final String MAC_SCRIPT =
        "if application \"Spotify\" is running then\n" +
        "tell application \"Spotify\" to return (artist of current track) & tab & (name of current track) & tab & (player position) & tab & (duration of current track) & tab & (player state as string)\n" +
        "else if application \"Music\" is running then\n" +
        "tell application \"Music\" to return (artist of current track) & tab & (name of current track) & tab & (player position) & tab & ((duration of current track) * 1000) & tab & (player state as string)\n" +
        "end if";

    private static final String WIN_SCRIPT = String.join("\n",
        "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
        "$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
        "Function Await($op,$type){ $t=$asTask.MakeGenericMethod($type); $n=$t.Invoke($null,@($op)); $n.Wait(-1)|Out-Null; $n.Result }",
        "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null",
        "$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
        "$s=$mgr.GetCurrentSession(); if(-not $s){exit}",
        "$p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
        "$t=$s.GetTimelineProperties(); $i=$s.GetPlaybackInfo()",
        "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8",
        "\"$($p.Artist)`t$($p.Title)`t$([int64]$t.Position.TotalMilliseconds)`t$([int64]$t.EndTime.TotalMilliseconds)`t$($i.PlaybackStatus)\"");

    private static String[] windowsCmd() throws IOException {
        if (winScript == null) {
            winScript = Files.createTempFile("lyricpop", ".ps1");
            Files.writeString(winScript, WIN_SCRIPT, StandardCharsets.UTF_8);
            winScript.toFile().deleteOnExit();
        }
        return new String[]{"powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", winScript.toString()};
    }
}
