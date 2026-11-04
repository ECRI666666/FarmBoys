package de.ecri.farmboys.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.ecri.farmboys.FarmBoysMod;
import de.ecri.farmboys.entity.ChestBoyEntity;
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

public class ChestBoyRenderer extends EntityRenderer<ChestBoyEntity, ChestBoyRenderer.State> {
    public static final Identifier WHITE = FarmBoysMod.id("textures/entity/white.png");

    public static class State extends EntityRenderState {
        float yaw;
        int itemCount;
        boolean hurt;
        boolean dead;
        boolean lidOpen;
        boolean tripping;
        float age;
        float legSwing;
    }

    public ChestBoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.38f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChestBoyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot(partialTick);
        state.itemCount = entity.getItemCount();
        state.hurt = entity.hurtTime > 0;
        state.dead = entity.isDeadOrDying();
        state.lidOpen = entity.isLidOpen();
        state.tripping = entity.isClumsyTripping();
        state.age = entity.tickCount + partialTick;
        state.legSwing = (float) Math.sin((entity.tickCount + partialTick) * 0.45f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam) {
        pose.pushPose();
        pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));

        if (s.dead) {
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(80)));
        } else if (s.tripping) {
            pose.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(20))); // Faceplant on trip!
        }

        int ov = OverlayTexture.pack(0, s.hurt ? 3 : 10);
        int light = s.lightCoords;

        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, vc) -> {
            // Main Chest Body Box (Oak wood)
            box(p, vc, 0, 0.22f, 0, 0.42f, 0.20f, 0.42f, 0xFF8D6E63, light, ov);
            // Dark iron rim corners
            box(p, vc, 0, 0.22f, 0, 0.43f, 0.04f, 0.43f, 0xFF3E2723, light, ov);

            // Chest Lid (Wood & Hinges)
            if (s.lidOpen) {
                // Open lid angled back
                box(p, vc, 0, 0.40f, -0.12f, 0.44f, 0.10f, 0.36f, 0xFF6D4C41, light, ov);
            } else {
                // Closed lid
                box(p, vc, 0, 0.36f, 0, 0.44f, 0.10f, 0.44f, 0xFF6D4C41, light, ov);
            }

            // Golden lock latch on front
            box(p, vc, 0, 0.26f, 0.22f, 0.08f, 0.10f, 0.03f, 0xFFFFCA28, light, ov);

            // Cute little eyes on the lock latch!
            box(p, vc, -0.025f, 0.28f, 0.236f, 0.02f, 0.03f, 0.01f, 0xFF1A1A1A, light, ov);
            box(p, vc, 0.025f, 0.28f, 0.236f, 0.02f, 0.03f, 0.01f, 0xFF1A1A1A, light, ov);

            // 4 brass robot legs that walk
            float fSwing = s.legSwing * 0.05f;
            float bSwing = -s.legSwing * 0.05f;

            // Front Left & Right
            box(p, vc, -0.15f, 0.06f, 0.12f + fSwing, 0.06f, 0.14f, 0.06f, 0xFF455A64, light, ov);
            box(p, vc, -0.15f, 0.01f, 0.13f + fSwing, 0.08f, 0.03f, 0.10f, 0xFFFFB300, light, ov);

            box(p, vc, 0.15f, 0.06f, 0.12f + bSwing, 0.06f, 0.14f, 0.06f, 0xFF455A64, light, ov);
            box(p, vc, 0.15f, 0.01f, 0.13f + bSwing, 0.08f, 0.03f, 0.10f, 0xFFFFB300, light, ov);

            // Back Left & Right
            box(p, vc, -0.15f, 0.06f, -0.12f + bSwing, 0.06f, 0.14f, 0.06f, 0xFF455A64, light, ov);
            box(p, vc, -0.15f, 0.01f, -0.11f + bSwing, 0.08f, 0.03f, 0.10f, 0xFFFFB300, light, ov);

            box(p, vc, 0.15f, 0.06f, -0.12f + fSwing, 0.06f, 0.14f, 0.06f, 0xFF455A64, light, ov);
            box(p, vc, 0.15f, 0.01f, -0.11f + fSwing, 0.08f, 0.03f, 0.10f, 0xFFFFB300, light, ov);
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
