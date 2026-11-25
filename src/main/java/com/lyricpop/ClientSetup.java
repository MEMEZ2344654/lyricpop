package com.lyricpop;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

final class ClientSetup {
    static KeyMapping toggle, pops;

    static void init(IEventBus modBus) {
        LyricsManager.start();
        toggle = new KeyMapping("key.lyricpop.toggle", GLFW.GLFW_KEY_Y, "key.categories.misc");
        pops = new KeyMapping("key.lyricpop.floating", GLFW.GLFW_KEY_U, "key.categories.misc");

        modBus.addListener((RegisterKeyMappingsEvent e) -> { e.register(toggle); e.register(pops); });

        // Forge 1.21: the HUD is drawn through gui layers instead of RenderGuiEvent
        modBus.addListener((AddGuiOverlayLayersEvent e) -> {
            ResourceLocation stackName = ResourceLocation.fromNamespaceAndPath("lyricpop", "lyrics_stack");
            ForgeLayeredDraw stack = new ForgeLayeredDraw(stackName);
            stack.add(ResourceLocation.fromNamespaceAndPath("lyricpop", "lyrics_hud"),
                    (gg, tracker) -> LyricHud.render(gg));
            e.getLayeredDraw().add(stackName, stack, () -> true);
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (toggle.consumeClick()) {
                LyricHud.enabled = !LyricHud.enabled;
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("Lyrics " + (LyricHud.enabled ? "on" : "off")), true);
            }
            while (pops.consumeClick()) {
                LyricHud.floating = !LyricHud.floating;
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("Popup words " + (LyricHud.floating ? "on" : "off")), true);
            }
        });
    }
}
