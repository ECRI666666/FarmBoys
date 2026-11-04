package de.ecri.farmbots.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.ecri.farmbots.FarmBotsMod;
import de.ecri.farmbots.entity.CanBotEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class CanBotRenderer extends EntityRenderer<CanBotEntity, CanBotRenderer.State> {
    public static final Identifier WHITE = FarmBotsMod.id("textures/entity/white.png");

    public static class State extends EntityRenderState {
        float yaw;
        boolean hurt;
        boolean dead;
        boolean watering;
        boolean clumsy;
        float age;
        float legSwing;
    }

    public CanBotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CanBotEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot(partialTick);
        state.hurt = entity.hurtTime > 0;
        state.dead = entity.isDeadOrDying();
        state.watering = entity.isWatering();
        state.clumsy = entity.isClumsySlipping();
        state.age = entity.tickCount + partialTick;
        state.legSwing = (float) Math.sin((entity.tickCount + partialTick) * 0.45f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam) {
        pose.pushPose();
        pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));

        if (s.dead) {
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(80)));
        } else if (s.watering) {
            pose.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(24)));
        } else if (s.clumsy) {
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(s.legSwing * 20.0f)));
        }

        int ov = OverlayTexture.pack(0, s.hurt ? 3 : 10);
        int light = s.lightCoords;

        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, vc) -> {
            // Main watering can body (Turquoise / Teal)
            box(p, vc, 0, 0.28f, 0, 0.38f, 0.30f, 0.38f, 0xFF26A69A, light, ov);
            // Top lid / rim (Darker Teal)
            box(p, vc, 0, 0.44f, 0, 0.32f, 0.04f, 0.32f, 0xFF00796B, light, ov);

            // Spout in front: angled stem and brass rose/shower head
            box(p, vc, 0, 0.26f, 0.26f, 0.10f, 0.10f, 0.22f, 0xFF00897B, light, ov);
            box(p, vc, 0, 0.33f, 0.38f, 0.16f, 0.12f, 0.08f, 0xFFFFCA28, light, ov); // Brass rose

            // Curved handle in back
            box(p, vc, 0, 0.34f, -0.25f, 0.06f, 0.24f, 0.14f, 0xFF004D40, light, ov);

            // Cute big black eyes on front of can
            box(p, vc, -0.10f, 0.32f, 0.195f, 0.06f, 0.06f, 0.01f, 0xFF1B1B1B, light, ov);
            box(p, vc, 0.10f, 0.32f, 0.195f, 0.06f, 0.06f, 0.01f, 0xFF1B1B1B, light, ov);
            // Eye gleam
            box(p, vc, -0.09f, 0.34f, 0.198f, 0.02f, 0.02f, 0.01f, 0xFFFFFFFF, light, ov);
            box(p, vc, 0.11f, 0.34f, 0.198f, 0.02f, 0.02f, 0.01f, 0xFFFFFFFF, light, ov);

            // Two cute waddling robot legs
            float leftSwing = s.legSwing * 0.06f;
            float rightSwing = -s.legSwing * 0.06f;
            box(p, vc, -0.12f, 0.08f, leftSwing, 0.08f, 0.16f, 0.08f, 0xFF37474F, light, ov);
            box(p, vc, -0.12f, 0.02f, leftSwing + 0.02f, 0.10f, 0.04f, 0.14f, 0xFFFFB300, light, ov);

            box(p, vc, 0.12f, 0.08f, rightSwing, 0.08f, 0.16f, 0.08f, 0xFF37474F, light, ov);
            box(p, vc, 0.12f, 0.02f, rightSwing + 0.02f, 0.10f, 0.04f, 0.14f, 0xFFFFB300, light, ov);
        });

        pose.popPose();
        super.submit(s, pose, c, cam);
    }

    private static final float[][][] F = {
        {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}, {-1, 0, 0}},
        {{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, 0, 0}},
        {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {0, -1, 0}},
        {{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}, {0, 1, 0}},
        {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}, {0, 0, -1}},
        {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}, {0, 0, 1}}
    };
    private static final float[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

    public static void box(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float w, float h, float d, int color, int light, int ov) {
        float hw = w / 2, hh = h / 2, hd = d / 2;
        Vector3f v = new Vector3f();
        Vector3f nrm = new Vector3f();
        Matrix4f m = new Matrix4f().translate(cx, cy, cz);
        for (int f = 0; f < 6; f++) {
            float[][] q = F[f];
            m.transformDirection(nrm.set(q[4][0], q[4][1], q[4][2])).normalize();
            for (int i = 0; i < 4; i++) {
                m.transformPosition(v.set(q[i][0] * hw, q[i][1] * hh, q[i][2] * hd));
                vc.addVertex(pose, v.x, v.y, v.z)
                  .setColor(color)
                  .setUv(UV[i][0], UV[i][1])
                  .setOverlay(ov)
                  .setLight(light)
                  .setNormal(pose, nrm.x, nrm.y, nrm.z);
            }
        }
    }
}
