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
import java.net.URLDecoder;
import java.nio.file.Files;
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
            server.createContext("/api/fonts", SettingsServer::handleFonts);
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

    // ---------- fonts ----------
    // These endpoints deliberately send no CORS headers and only accept requests that come from the
    // settings page itself, so other websites can't add or delete fonts.

    private static void handleFonts(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if ("GET".equalsIgnoreCase(method) && "/api/fonts".equals(path)) {
            JsonArray arr = new JsonArray();
            for (String f : LyricFont.files()) {
                JsonObject o = new JsonObject();
                o.addProperty("file", f);
                o.addProperty("name", f.substring(0, f.lastIndexOf('.')));
                arr.add(o);
            }
            JsonObject out = new JsonObject();
            out.add("fonts", arr);
            sendLocal(ex, 200, out);
            return;
        }

        // The settings page loads the chosen font from here so it can use it for its own text.
        // No CORS headers, so other websites can't read it.
        if ("GET".equalsIgnoreCase(method) && "/api/fonts/file".equals(path)) {
            String name = queryParam(ex, "name");
            if (name == null || !LyricFont.files().contains(name)) {
                sendLocal(ex, 404, error("No such font"));
                return;
            }
            byte[] bytes = Files.readAllBytes(LyricFont.fontsDir().resolve(name));
            ex.getResponseHeaders().set("Content-Type", name.toLowerCase(java.util.Locale.ROOT).endsWith(".otf") ? "font/otf" : "font/ttf");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        if (!fromSettingsPage(ex)) {
            sendLocal(ex, 403, error("Not allowed"));
            return;
        }

        try {
            if ("POST".equalsIgnoreCase(method) && "/api/fonts".equals(path)) {
                String len = ex.getRequestHeaders().getFirst("Content-Length");
                if (len != null && Long.parseLong(len) > LyricFont.MAX_BYTES) {
                    sendLocal(ex, 413, error("That font is over 10 MB"));
                    return;
                }
                byte[] data = ex.getRequestBody().readNBytes(LyricFont.MAX_BYTES + 1);
                final String file = LyricFont.saveFont(queryParam(ex, "name"), data);
                // Switch to the new font only after Minecraft has reloaded, so we never draw a font that isn't loaded yet.
                LyricFont.reloadThen(() -> {
                    Config.fontFile = file;
                    Config.save();
                });
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("file", file);
                sendLocal(ex, 200, o);
                return;
            }

            if ("DELETE".equalsIgnoreCase(method) && "/api/fonts".equals(path)) {
                String name = queryParam(ex, "name");
                if (name != null && name.equals(Config.fontFile)) {
                    Config.fontFile = "";
                    Config.save();
                }
                if (!LyricFont.deleteFont(name)) {
                    sendLocal(ex, 404, error("No such font"));
                    return;
                }
                LyricFont.reloadThen(() -> { });
                sendLocal(ex, 200, ok());
                return;
            }

            if ("POST".equalsIgnoreCase(method) && "/api/fonts/open".equals(path)) {
                Files.createDirectories(LyricFont.fontsDir());
                try {
                    Desktop.getDesktop().open(LyricFont.fontsDir().toFile());
                } catch (Exception e) {
                    sendLocal(ex, 500, error("Could not open the folder: " + LyricFont.fontsDir()));
                    return;
                }
                sendLocal(ex, 200, ok());
                return;
            }

            sendLocal(ex, 404, error("Not found"));
        } catch (Exception e) {
            sendLocal(ex, 400, error(String.valueOf(e.getMessage())));
        }
    }

    private static boolean fromSettingsPage(HttpExchange ex) {
        if (!"1".equals(ex.getRequestHeaders().getFirst("X-LyricPop"))) return false;
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        return origin.equals("http://localhost:" + PORT) || origin.equals("http://127.0.0.1:" + PORT);
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static void sendLocal(HttpExchange ex, int code, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
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
