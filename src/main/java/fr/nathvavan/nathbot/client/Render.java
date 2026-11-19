package fr.nathvavan.nathbot.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Rendu 3D simple (boites et lignes visibles a travers les murs). */
public final class Render {
    private Render() {}

    /** Type de rendu "lignes sans test de profondeur" (on voit a travers les blocs). */
    private static final class Types extends RenderType {
        private Types() {
            super("nathbot_dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.LINES, 256, false, false, () -> {}, () -> {});
        }

        static final RenderType LINES_XRAY = create("nathbot_lines_xray", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .createCompositeState(false));
    }

    public record Box(AABB box, int color) {}

    public record Line(Vec3 a, Vec3 b, int color) {}

    /** Collecte de ce qu'il faut dessiner pendant une frame. */
    public static final class Ctx {
        public final List<Box> boxes = new ArrayList<>();
        public final List<Line> lines = new ArrayList<>();
        public final float partialTick;
        public final Vec3 cam;

        Ctx(float pt, Vec3 cam) {
            this.partialTick = pt;
            this.cam = cam;
        }

        public void box(AABB b, int color) { boxes.add(new Box(b, color)); }

        public void line(Vec3 a, Vec3 b, int color) { lines.add(new Line(a, b, color)); }

        /** Point juste devant la camera (depart des "tracers"). */
        public Vec3 eyeAnchor() {
            Minecraft mc = Minecraft.getInstance();
            Vec3 look = Vec3.directionFromRotation(mc.gameRenderer.getMainCamera().getXRot(), mc.gameRenderer.getMainCamera().getYRot());
            return cam.add(look.scale(0.5));
        }
    }

    static Ctx newCtx(float pt, Vec3 cam) { return new Ctx(pt, cam); }

    static void draw(PoseStack ps, Ctx ctx) {
        if (ctx.boxes.isEmpty() && ctx.lines.isEmpty()) return;
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(Types.LINES_XRAY);
        ps.pushPose();
        ps.translate(-ctx.cam.x, -ctx.cam.y, -ctx.cam.z);
        for (Box b : ctx.boxes) {
            int c = b.color();
            LevelRenderer.renderLineBox(ps, vc, b.box(), r(c), g(c), bl(c), a(c));
        }
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (Line l : ctx.lines) {
            int c = l.color();
            Vec3 d = l.b().subtract(l.a()).normalize();
            vc.vertex(m, (float) l.a().x, (float) l.a().y, (float) l.a().z).color(r(c), g(c), bl(c), a(c)).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
            vc.vertex(m, (float) l.b().x, (float) l.b().y, (float) l.b().z).color(r(c), g(c), bl(c), a(c)).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
        }
        ps.popPose();
        buf.endBatch(Types.LINES_XRAY);
    }

    static float a(int c) { float v = ((c >>> 24) & 255) / 255f; return v == 0 ? 1f : v; }
    static float r(int c) { return ((c >> 16) & 255) / 255f; }
    static float g(int c) { return ((c >> 8) & 255) / 255f; }
    static float bl(int c) { return (c & 255) / 255f; }
}
