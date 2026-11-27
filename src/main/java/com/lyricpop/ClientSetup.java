package com.lyricpop;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

final class ClientSetup {
    static KeyMapping toggle, pops, settings, earlier, later;

    static void init(FMLJavaModLoadingContext ctx) {
        Config.load();
        LyricsManager.start();
        IEventBus modBus = ctx.getModEventBus();

        toggle = new KeyMapping("key.lyricpop.toggle", GLFW.GLFW_KEY_Y, "key.categories.lyricpop");
        pops = new KeyMapping("key.lyricpop.floating", GLFW.GLFW_KEY_U, "key.categories.lyricpop");
        settings = new KeyMapping("key.lyricpop.settings", GLFW.GLFW_KEY_J, "key.categories.lyricpop");
        earlier = new KeyMapping("key.lyricpop.earlier", GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.lyricpop");
        later = new KeyMapping("key.lyricpop.later", GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.lyricpop");

        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(toggle);
            e.register(pops);
            e.register(settings);
            e.register(earlier);
            e.register(later);
        });

        modBus.addListener((AddGuiOverlayLayersEvent e) -> {
            ResourceLocation stackName = ResourceLocation.fromNamespaceAndPath("lyricpop", "lyrics_stack");
            ForgeLayeredDraw stack = new ForgeLayeredDraw(stackName);
            stack.add(ResourceLocation.fromNamespaceAndPath("lyricpop", "lyrics_hud"),
                    (gg, tracker) -> LyricHud.render(gg));
            e.getLayeredDraw().add(stackName, stack, () -> true);
        });

        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> WorldLyrics.render(e));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) -> onTick());

        ctx.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }

    private static void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        while (toggle.consumeClick()) {
            Config.enabled = !Config.enabled;
            Config.save();
            msg(mc, "Lyrics " + (Config.enabled ? "on" : "off"));
        }
        while (pops.consumeClick()) {
            Config.popups = !Config.popups;
            Config.save();
            msg(mc, "Popup lyrics " + (Config.popups ? "on" : "off"));
        }
        while (settings.consumeClick()) {
            mc.setScreen(new SettingsScreen(null));
        }
        while (earlier.consumeClick()) {
            Config.syncOffset = Math.min(3000, Config.syncOffset + 100);
            Config.save();
            msg(mc, "Lyric timing: " + (Config.syncOffset >= 0 ? "+" : "") + Config.syncOffset + " ms (earlier)");
        }
        while (later.consumeClick()) {
            Config.syncOffset = Math.max(-3000, Config.syncOffset - 100);
            Config.save();
            msg(mc, "Lyric timing: " + (Config.syncOffset >= 0 ? "+" : "") + Config.syncOffset + " ms (later)");
        }
        LyricEngine.tick();
    }
}
