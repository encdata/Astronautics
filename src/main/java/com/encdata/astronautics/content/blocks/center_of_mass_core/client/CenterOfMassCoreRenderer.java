package com.encdata.astronautics.content.blocks.center_of_mass_core.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.content.blocks.center_of_mass_core.CenterOfMassCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class CenterOfMassCoreRenderer implements BlockEntityRenderer<CenterOfMassCoreBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Astronautics.MOD_ID, "textures/block/center_of_mass_core.png");

    public CenterOfMassCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CenterOfMassCoreBlockEntity core, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float rotation = (core.getLevel() == null ? 0.0F : core.getLevel().getGameTime() + partialTick) * 4.0F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5625F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5F));

        VertexConsumer black = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        drawCube(black, poseStack, 8.0F / 64.0F, 4.0F / 64.0F,
                9.0F / 64.0F, 5.0F / 64.0F, 0, 0, 0);

        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(TEXTURE));
        drawDisk(glow, poseStack, 0.0F, 0.0F, 2.0F / 64.0F, 2.0F / 64.0F,
                255, 255, 255);
        poseStack.popPose();
    }

    private static void drawDisk(VertexConsumer out, PoseStack poseStack,
                                 float u0, float v0, float u1, float v1,
                                 int red, int green, int blue) {
        PoseStack.Pose pose = poseStack.last();

        quad(out, pose, -0.25F, 0.0F, -0.25F, -0.25F, 0.0F, 0.25F,
                0.25F, 0.0F, 0.25F, 0.25F, 0.0F, -0.25F, u0, v0, u1, v1, 0, 1, 0, red, green, blue);
        quad(out, pose, -0.25F, 0.0F, -0.25F, 0.25F, 0.0F, -0.25F,
                0.25F, 0.0F, 0.25F, -0.25F, 0.0F, 0.25F, u0, v0, u1, v1, 0, -1, 0, red, green, blue);
    }

    private static void drawCube(VertexConsumer out, PoseStack poseStack,
                                 float u0, float v0, float u1, float v1,
                                 int red, int green, int blue) {
        PoseStack.Pose pose = poseStack.last();
        float min = -0.125F;
        float max = 0.125F;
        quad(out, pose, min, min, max, max, min, max, max, max, max, min, max, max, u0, v0, u1, v1, 0, 0, 1, red, green, blue);
        quad(out, pose, max, min, min, min, min, min, min, max, min, max, max, min, u0, v0, u1, v1, 0, 0, -1, red, green, blue);
        quad(out, pose, min, min, min, min, min, max, min, max, max, min, max, min, u0, v0, u1, v1, -1, 0, 0, red, green, blue);
        quad(out, pose, max, min, max, max, min, min, max, max, min, max, max, max, u0, v0, u1, v1, 1, 0, 0, red, green, blue);
        quad(out, pose, min, max, max, max, max, max, max, max, min, min, max, min, u0, v0, u1, v1, 0, 1, 0, red, green, blue);
        quad(out, pose, min, min, min, max, min, min, max, min, max, min, min, max, u0, v0, u1, v1, 0, -1, 0, red, green, blue);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz,
                             int red, int green, int blue) {
        vertex(out, pose, ax, ay, az, u0, v0, nx, ny, nz, red, green, blue);
        vertex(out, pose, bx, by, bz, u1, v0, nx, ny, nz, red, green, blue);
        vertex(out, pose, cx, cy, cz, u1, v1, nx, ny, nz, red, green, blue);
        vertex(out, pose, dx, dy, dz, u0, v1, nx, ny, nz, red, green, blue);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz,
                               int red, int green, int blue) {
        out.addVertex(pose, x, y, z).setColor(red, green, blue, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
