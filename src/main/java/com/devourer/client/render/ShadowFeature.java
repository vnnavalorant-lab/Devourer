package com.devourer.client.render;

import com.devourer.DevourerMod;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * A "criatura sombria" da referência: cauda de escorpião segmentada, dois braços extras com garras
 * saindo das costas e o olho laranja brilhante dentro de uma máscara.
 */
public class ShadowFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEX = new Identifier(DevourerMod.ID, "textures/entity/shadow.png");
    private static final Identifier GOGGLE = new Identifier(DevourerMod.ID, "textures/entity/goggle.png");
    private static final Identifier GLOW = new Identifier(DevourerMod.ID, "textures/entity/goggle_glow.png");

    private static final ModelPart SEG = Models.box(0, 0, -1.6f, 0, -1.6f, 3, 5, 3);
    private static final ModelPart SPIKE = Models.box(0, 16, -0.5f, -3, -0.5f, 1, 3, 1);
    private static final ModelPart STINGER = Models.box(0, 0, -1.2f, 0, -1.2f, 2, 8, 2);
    private static final ModelPart UPPER = Models.box(0, 0, -1.3f, 0, -1.3f, 2, 7, 2);
    private static final ModelPart CLAW = Models.box(0, 32, -0.5f, 0, -0.5f, 1, 6, 1);
    private static final ModelPart RING = Models.box(0, 0, -2.5f, -2.5f, -0.5f, 5, 5, 1, 16, 16);

    public ShadowFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity pl,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (pl.isInvisible()) return;
        float t = pl.age + tickDelta;
        int ov = OverlayTexture.DEFAULT_UV;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));

        // ---- cauda de escorpião ----
        m.push();
        m.translate(0, 9f / 16f, 2.5f / 16f);
        for (int i = 0; i < 10; i++) {
            float ang = (i == 0 ? 1.2f : 0.4f) + (float) Math.sin(t * 0.07f + i * 0.5f) * 0.04f;
            m.multiply(RotationAxis.POSITIVE_X.rotation(ang));
            (i == 9 ? STINGER : SEG).render(m, vc, light, ov);
            if (i < 9) {
                m.push();
                m.translate(0, 2.5f / 16f, 1.4f / 16f);
                SPIKE.render(m, vc, light, ov);
                m.pop();
            }
            m.translate(0, 5f / 16f, 0);
            m.scale(0.93f, 0.93f, 0.93f);
        }
        m.pop();

        // ---- dois braços extras com garras ----
        for (int s = -1; s <= 1; s += 2) {
            float sw = (float) Math.sin(t * 0.06f + s) * 0.15f;
            m.push();
            m.translate(s * 3.5f / 16f, 1.5f / 16f, 3f / 16f);
            m.multiply(RotationAxis.POSITIVE_Z.rotation(s * (0.5f + sw)));
            m.multiply(RotationAxis.POSITIVE_X.rotation(2.4f + sw));
            UPPER.render(m, vc, light, ov);
            m.translate(0, 7f / 16f, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(0.9f + (float) Math.sin(t * 0.09f + s * 2f) * 0.2f));
            UPPER.render(m, vc, light, ov);
            m.translate(0, 7f / 16f, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotation(0.8f));
            for (int c = -1; c <= 1; c++) {
                m.push();
                m.multiply(RotationAxis.POSITIVE_Z.rotation(c * 0.45f));
                m.multiply(RotationAxis.POSITIVE_X.rotation(0.25f + (float) Math.sin(t * 0.2f + c) * 0.1f));
                CLAW.render(m, vc, light, ov);
                m.pop();
            }
            m.pop();
        }

        // ---- olho laranja brilhante dentro da máscara ----
        m.push();
        getContextModel().head.rotate(m);
        m.translate(2.2f / 16f, -3.8f / 16f, -4.3f / 16f);
        RING.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(GOGGLE)), light, ov);
        m.scale(1.04f, 1.04f, 1.04f);
        RING.render(m, vcp.getBuffer(RenderLayer.getEyes(GLOW)), 0xF000F0, ov);
        m.pop();
    }
}
