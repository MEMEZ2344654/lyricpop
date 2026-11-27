package com.lyricpop;

import com.google.gson.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Tiny localhost server that serves the browser settings page and a simple config API.
 * GET  /api/config  -> current settings as JSON
 * POST /api/config  -> update settings from JSON body
 * GET  /            -> the HTML settings page
 */
public final class SettingsServer {
    public static final int PORT = 28765;
    private static HttpServer server;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SettingsServer() {}

    public static synchronized void start() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/", SettingsServer::handleRoot);
            server.createContext("/api/config", SettingsServer::handleConfig);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "LyricPop-Web");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            System.out.println("[LyricPop] Settings server started on http://localhost:" + PORT);
        } catch (Exception e) {
            System.err.println("[LyricPop] Failed to start settings server: " + e.getMessage());
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static void openInBrowser() {
        start();
        String url = "http://localhost:" + PORT;

        // Always show a clickable message in chat
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var link = net.minecraft.network.chat.Component.literal(url)
                .withStyle(s -> s
                    .withColor(net.minecraft.ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        net.minecraft.network.chat.Component.literal("Click to open settings"))));

            var msg = net.minecraft.network.chat.Component.literal("LyricPop settings: ").append(link);
            mc.player.displayClientMessage(msg, false);
        }

        // Also try to open the browser automatically
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            System.out.println("[LyricPop] Could not open browser automatically. Use the link in chat: " + url);
        }
    }

    // ---------- handlers ----------

    private static void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "Method not allowed");
            return;
        }
        byte[] html = loadHtml();
        if (html == null) {
            send(ex, 500, "text/plain", "settings.html not found in mod resources");
            return;
        }
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/html; charset=utf-8");
        h.set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, html.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(html);
        }
    }

    private static void handleConfig(HttpExchange ex) throws IOException {
        addCors(ex);
        String method = ex.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            String json = GSON.toJson(Config.toJson());
            send(ex, 200, "application/json", json);
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                Config.fromJson(obj);
                Config.save();
                send(ex, 200, "application/json", "{\"ok\":true}");
            } catch (Exception e) {
                send(ex, 400, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
            return;
        }

        send(ex, 405, "text/plain", "Method not allowed");
    }

    private static void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", type + "; charset=utf-8");
        addCors(ex);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static byte[] loadHtml() {
        try (InputStream in = SettingsServer.class.getResourceAsStream("/assets/lyricpop/settings.html")) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
