package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;

public final class SpaceSuitArmorModel extends HumanoidModel<LivingEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Astronautics.MOD_ID, "textures/entity/space_suit.png");
    private static SpaceSuitArmorModel instance;
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "space_suit"), "main");

    public SpaceSuitArmorModel(ModelPart root) { super(root, ignored -> RenderType.armorCutoutNoCull(TEXTURE)); }

    public static SpaceSuitArmorModel instance() {
        if (instance == null) instance = new SpaceSuitArmorModel(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
        return instance;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("helmet_shell", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.501F))
                .texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.801F))
                .texOffs(1, 65).addBox(-0.95F, -1.625F, 4.6F, 2, 4, 1, new CubeDeformation(0.125F)), PartPose.ZERO);
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("suit_body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, new CubeDeformation(0.252F))
                .texOffs(16, 32).addBox(-4, 0, -2, 8, 12, 4, new CubeDeformation(0.501F))
                .texOffs(64, 0).addBox(-4, 0, 2.5F, 8, 10, 4, new CubeDeformation(0.252F))
                .texOffs(8, 64).addBox(-2, 5.2F, 6.7F, 4, 4, 1), PartPose.ZERO);
        body.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(0, 68)
                .addBox(0, -9, 0, 0, 11, 2), PartPose.offsetAndRotation(4.25F, 0.75F, 6.75F, 0, 0.7854F, 0));
        root.getChild("right_arm").addOrReplaceChild("suit_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, new CubeDeformation(0.251F))
                .texOffs(40, 32).addBox(-3, -2, -2, 4, 12, 4, new CubeDeformation(0.501F)), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild("suit_arm", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4, new CubeDeformation(0.251F))
                .texOffs(48, 48).addBox(-1, -2, -2, 4, 12, 4, new CubeDeformation(0.501F)), PartPose.ZERO);
        root.getChild("right_leg").addOrReplaceChild("suit_leg", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-1.9F, 0, -2, 4, 12, 4, new CubeDeformation(0.251F))
                .texOffs(0, 32).addBox(-1.9F, 0, -2, 4, 12, 4, new CubeDeformation(0.501F)), PartPose.ZERO);
        root.getChild("left_leg").addOrReplaceChild("suit_leg", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-2.1F, 0, -2, 4, 12, 4, new CubeDeformation(0.251F))
                .texOffs(0, 48).addBox(-2.1F, 0, -2, 4, 12, 4, new CubeDeformation(0.501F)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 88, 88);
    }

    public void showSlot(EquipmentSlot slot) {
        setAllVisible(false);
        switch (slot) {
            case HEAD -> head.visible = true;
            case CHEST -> { body.visible = true; rightArm.visible = true; leftArm.visible = true; }
            case LEGS -> { body.visible = true; rightLeg.visible = true; leftLeg.visible = true; }
            case FEET -> { rightLeg.visible = true; leftLeg.visible = true; }
            default -> { }
        }
    }
}
