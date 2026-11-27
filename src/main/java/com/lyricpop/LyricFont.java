package com.lyricpop;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Lets players use their own TTF/OTF font for the lyrics.
 * Font files live in config/lyricpop/fonts. At startup the mod copies them into a small hidden
 * resource pack (config/lyricpop/fontpack) that Minecraft loads like any other font.
 */
final class LyricFont {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final String NAMESPACE = "lyricpop";

    /** font file name -> font id inside the pack. Swapped as a whole, so it is safe to read from the render thread. */
    private static volatile Map<String, String> ids = Map.of();

    private LyricFont() {}

    // ---------------------------------------------------------------- paths

    static Path fontsDir() { return FMLPaths.CONFIGDIR.get().resolve("lyricpop").resolve("fonts"); }
    private static Path packDir() { return FMLPaths.CONFIGDIR.get().resolve("lyricpop").resolve("fontpack"); }

    // ---------------------------------------------------------------- using the font

    /** The font to draw with, or null for Minecraft's normal font. */
    static ResourceLocation current() {
        String f = Config.fontFile;
        if (f == null || f.isEmpty()) return null;
        String id = ids.get(f);
        return id == null ? null : ResourceLocation.fromNamespaceAndPath(NAMESPACE, id);
    }

    static boolean active() { return current() != null; }

    /** Text styled with the chosen font (and bold if asked). Use this for every piece of lyric text. */
    static MutableComponent text(String s, boolean bold) {
        ResourceLocation id = current();
        return Component.literal(s).withStyle(st -> {
            Style r = st;
            if (id != null) r = r.withFont(id);
            if (bold) r = r.withBold(true);
            return r;
        });
    }

    // ---------------------------------------------------------------- font files

    static List<String> files() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(fontsDir())) {
            s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String l = n.toLowerCase(Locale.ROOT);
                        return l.endsWith(".ttf") || l.endsWith(".otf");
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException ignored) { }
        return out;
    }

    /** Checks the first bytes: TrueType (00 01 00 00 or "true") or OpenType/CFF ("OTTO"). */
    static boolean isFont(byte[] d) {
        if (d == null || d.length < 1024 || d.length > MAX_BYTES) return false;
        int m = ((d[0] & 255) << 24) | ((d[1] & 255) << 16) | ((d[2] & 255) << 8) | (d[3] & 255);
        return m == 0x00010000 || m == 0x74727565 || m == 0x4F54544F;
    }

    static boolean isOtf(byte[] d) {
        return d.length > 3 && d[0] == 'O' && d[1] == 'T' && d[2] == 'T' && d[3] == 'O';
    }

    /** Safe file name: no folders, plain characters, right extension. */
    static String cleanName(String raw, boolean otf) {
        String n = raw == null ? "font" : raw;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replaceAll("[^A-Za-z0-9 _().-]", "_").trim();
        if (n.isEmpty()) n = "font";
        if (n.length() > 60) n = n.substring(0, 60);
        return n + (otf ? ".otf" : ".ttf");
    }

    static synchronized String saveFont(String rawName, byte[] data) throws IOException {
        if (!isFont(data)) throw new IOException("That doesn't look like a TTF or OTF font file");
        String name = cleanName(rawName, isOtf(data));
        Files.createDirectories(fontsDir());
        Files.write(fontsDir().resolve(name), data);
        rebuild();
        return name;
    }

    static synchronized boolean deleteFont(String name) throws IOException {
        if (!files().contains(name)) return false;     // only exact names that really are in the folder
        Files.deleteIfExists(fontsDir().resolve(name));
        rebuild();
        return true;
    }

    // ---------------------------------------------------------------- the hidden resource pack

    /** Copies every font into the pack folder and writes a font definition for each. */
    static synchronized void rebuild() {
        try {
            Files.createDirectories(fontsDir());
            Path assets = packDir().resolve("assets");
            deleteTree(assets);
            Path fontDir = assets.resolve(NAMESPACE).resolve("font");
            Files.createDirectories(fontDir);
            Files.writeString(packDir().resolve("pack.mcmeta"),
                    "{\"pack\":{\"description\":\"LyricPop fonts\",\"pack_format\":34}}", StandardCharsets.UTF_8);

            Map<String, String> map = new LinkedHashMap<>();
            Set<String> used = new HashSet<>();
            for (String file : files()) {
                String base = slug(file), id = base;
                int n = 2;
                while (!used.add(id)) id = base + "_" + n++;
                String ext = file.toLowerCase(Locale.ROOT).endsWith(".otf") ? ".otf" : ".ttf";
                Files.copy(fontsDir().resolve(file), fontDir.resolve(id + ext), StandardCopyOption.REPLACE_EXISTING);
                // our font first, Minecraft's own font as a fallback for symbols the file doesn't have
                String json = "{\"providers\":[{\"type\":\"ttf\",\"file\":\"" + NAMESPACE + ":" + id + ext
                        + "\",\"shift\":[0.0,0.0],\"size\":11.0,\"oversample\":4.0},"
                        + "{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}";
                Files.writeString(fontDir.resolve(id + ".json"), json, StandardCharsets.UTF_8);
                map.put(file, id);
            }
            ids = Map.copyOf(map);
        } catch (Exception e) {
            System.err.println("[LyricPop] Could not set up custom fonts: " + e.getMessage());
        }
    }

    private static String slug(String file) {
        String n = file;
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return n.isEmpty() ? "font" : n;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            all.sort(Comparator.reverseOrder());   // children before parents
            for (Path p : all) Files.deleteIfExists(p);
        }
    }

    /** Registers the pack with Minecraft (always on, hidden from the pack screen's on/off list). */
    static void addPack(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        try {
            PackLocationInfo info = new PackLocationInfo("lyricpop_fonts", Component.literal("LyricPop Fonts"),
                    PackSource.DEFAULT, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(packDir()),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) e.addRepositorySource(consumer -> consumer.accept(pack));
        } catch (Exception ex) {
            System.err.println("[LyricPop] Could not register the font pack: " + ex.getMessage());
        }
    }

    /** Reloads resources (like F3+T) so Minecraft picks up new fonts, then runs {@code after} on the game thread. */
    static void reloadThen(Runnable after) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.reloadResourcePacks().thenRun(() -> mc.execute(after)));
    }
}
