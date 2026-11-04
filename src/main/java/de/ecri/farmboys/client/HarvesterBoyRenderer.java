package de.ecri.farmboys.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.ecri.farmboys.FarmBoysMod;
import de.ecri.farmboys.entity.HarvesterBoyEntity;
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

public class HarvesterBoyRenderer extends EntityRenderer<HarvesterBoyEntity, HarvesterBoyRenderer.State> {
    public static final Identifier WHITE = FarmBoysMod.id("textures/entity/white.png");

    public static class State extends EntityRenderState {
        float yaw;
        boolean hurt;
        boolean dead;
        boolean cutting;
        boolean recoiling;
        float age;
        float wheelRot;
    }

    public HarvesterBoyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HarvesterBoyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot(partialTick);
        state.hurt = entity.hurtTime > 0;
        state.dead = entity.isDeadOrDying();
        state.cutting = entity.isCutting();
        state.recoiling = entity.isRecoiling();
        state.age = entity.tickCount + partialTick;
        state.wheelRot = (entity.tickCount + partialTick) * 0.4f;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam) {
        pose.pushPose();
        pose.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(-s.yaw)));

        if (s.dead) {
            pose.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(80)));
        } else if (s.recoiling) {
            pose.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-20))); // Pop up front comically!
        }

        int ov = OverlayTexture.pack(0, s.hurt ? 3 : 10);
        int light = s.lightCoords;

        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, vc) -> {
            // Main Tractor Cabin & Chassis (Vibrant Harvest Orange)
            box(p, vc, 0, 0.22f, 0, 0.46f, 0.24f, 0.46f, 0xFFE64A19, light, ov);
            // Engine Hood (Darker Red-Orange)
            box(p, vc, 0, 0.35f, -0.04f, 0.36f, 0.04f, 0.38f, 0xFFBF360C, light, ov);

            // Exhaust Smokestack in back
            box(p, vc, -0.12f, 0.44f, -0.16f, 0.06f, 0.16f, 0.06f, 0xFF37474F, light, ov);

            // Status light on top
            int beaconColor = (s.recoiling || s.hurt) ? 0xFFFF1744 : (s.cutting ? 0xFFFFD600 : 0xFF76FF03);
            box(p, vc, 0.10f, 0.38f, -0.10f, 0.08f, 0.04f, 0.08f, beaconColor, light, ov);

            // Front rotating cutter reel / sickle blades (Yellow and steel)
            float reelSpin = (s.age * 25.0f) % 360.0f;
            box(p, vc, 0, 0.12f, 0.28f, 0.54f, 0.12f, 0.16f, 0xFFFFB300, light, ov); // Guard
            // Cutting blades
            box(p, vc, 0, 0.10f, 0.37f, 0.50f, 0.03f, 0.06f, 0xFFECEFF1, light, ov);

            // Cute headlight eyes on the front
            box(p, vc, -0.14f, 0.24f, 0.235f, 0.08f, 0.08f, 0.01f, 0xFFFFF59D, light, ov);
            box(p, vc, 0.14f, 0.24f, 0.235f, 0.08f, 0.08f, 0.01f, 0xFFFFF59D, light, ov);
            // Black pupils
            box(p, vc, -0.13f, 0.23f, 0.238f, 0.04f, 0.04f, 0.01f, 0xFF212121, light, ov);
            box(p, vc, 0.13f, 0.23f, 0.238f, 0.04f, 0.04f, 0.01f, 0xFF212121, light, ov);

            // Side caterpillar track wheels (Dark charcoal rubber)
            box(p, vc, -0.25f, 0.10f, 0, 0.08f, 0.18f, 0.48f, 0xFF212121, light, ov);
            box(p, vc, 0.25f, 0.10f, 0, 0.08f, 0.18f, 0.48f, 0xFF212121, light, ov);
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
