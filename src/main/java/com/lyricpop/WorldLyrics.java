package com.lyricpop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** Draws popup words as floating text inside the 3D world. */
final class WorldLyrics {
    private static final int FULL_BRIGHT = 15728880;

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!Config.enabled || !Config.popups || Config.mode == Config.Mode.SCREEN) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LyricEngine.POPS.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 c = cam.getPosition();
        Vector3f look = cam.getLookVector();
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        Font font = mc.font;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font.DisplayMode mode = Config.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        for (LyricEngine.Pop p : LyricEngine.POPS) {
            if (!p.world) continue;
            float t = LyricEngine.age(p);
            if (t > Config.popupLifetime) continue;

            double x, y, z;
            if (Config.lockToWorld) {
                x = p.wx; y = p.wy; z = p.wz;
            } else {
                x = c.x + look.x() * p.od + left.x() * p.ox + up.x() * p.oy;
                y = c.y + look.y() * p.od + left.y() * p.ox + up.y() * p.oy;
                z = c.z + look.z() * p.od + left.z() * p.ox + up.z() * p.oy;
            }
            y += t * 0.0006 * Config.drift;

            float s = 0.012f * p.od * p.size * Config.popupScale * Config.worldScale * LyricEngine.back(t);
            int alpha = LyricEngine.alpha(t);

            PoseStack ps = new PoseStack();
            ps.mulPose(e.getProjectionMatrix());
            ps.translate(x - c.x, y - c.y, z - c.z);
            ps.mulPose(cam.rotation());
            ps.mulPose(Axis.ZP.rotationDegrees(p.rot));
            ps.scale(s, -s, s);

            int w = font.width(p.text);
            font.drawInBatch(p.text, -w / 2f, -4f, (alpha << 24) | p.color, Config.popupShadow,
                    ps.last().pose(), buf, mode, 0, FULL_BRIGHT);
        }
        buf.endBatch();
    }
}
