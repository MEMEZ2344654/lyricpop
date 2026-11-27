package com.lyricpop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/**
 * Draws popup words as real 3D text in the world.
 * You can walk around and through them.
 */
final class WorldLyrics {
    private static final int FULL_BRIGHT = 15728880;

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!Config.enabled || !Config.popups || Config.mode == Config.Mode.SCREEN) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LyricEngine.POPS.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font.DisplayMode mode = Config.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        // Create our own PoseStack (getPoseStack() returns Matrix4f on this Forge version)
        PoseStack ps = new PoseStack();
        ps.pushPose();

        // Move into camera-relative space
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        for (LyricEngine.Pop p : LyricEngine.POPS) {
            if (!p.world) continue;
            float t = LyricEngine.age(p);
            if (t > Config.popupLifetime) continue;

            double x, y, z;
            if (Config.lockToWorld) {
                // Fixed world position — you can walk through it
                x = p.wx;
                y = p.wy + t * 0.0006 * Config.drift;
                z = p.wz;
            } else {
                // Follows the camera
                var look = cam.getLookVector();
                var left = cam.getLeftVector();
                var up = cam.getUpVector();
                x = camPos.x + look.x() * p.od + left.x() * p.ox + up.x() * p.oy;
                y = camPos.y + look.y() * p.od + left.y() * p.ox + up.y() * p.oy + t * 0.0006 * Config.drift;
                z = camPos.z + look.z() * p.od + left.z() * p.ox + up.z() * p.oy;
            }

            ps.pushPose();
            ps.translate(x, y, z);

            // Billboard — always face the camera
            Quaternionf rot = new Quaternionf(cam.rotation());
            ps.mulPose(rot);

            // Slight random tilt
            ps.mulPose(Axis.ZP.rotationDegrees(p.rot));

            // Scale (negative Y so text is upright)
            float s = 0.025f * p.size * Config.popupScale * Config.worldScale * LyricEngine.back(t);
            ps.scale(s, -s, s);

            int alpha = LyricEngine.alpha(t);
            int color = (alpha << 24) | (p.color & 0xFFFFFF);
            net.minecraft.network.chat.Component label = LyricFont.text(p.text, false);
            int w = font.width(label);

            font.drawInBatch(
                label,
                -w / 2f,
                -4f,
                color,
                Config.popupShadow,
                ps.last().pose(),
                buf,
                mode,
                0,
                FULL_BRIGHT
            );

            ps.popPose();
        }

        buf.endBatch();
        ps.popPose();
    }
}
