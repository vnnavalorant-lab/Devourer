package com.devourer.client.render;

import com.devourer.DevourPart;
import com.devourer.PartType;
import com.devourer.PlayerDevour;
import com.devourer.client.ClientData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Desenha as partes arrancadas (cabeça, braço, perna...) presas ao corpo do jogador,
 * usando a textura real da criatura devorada, levemente avermelhada/apodrecida.
 */
public class DevourFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Map<PartType, ModelPart> MODELS = new EnumMap<>(PartType.class);
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Identifier FALLBACK = new Identifier("textures/entity/zombie/zombie.png");

    static {
        MODELS.put(PartType.HEAD, Models.box(0, 0, -3, -6, -3, 6, 6, 6));
        MODELS.put(PartType.ARM, Models.box(16, 16, -1.5f, 0, -1.5f, 3, 9, 3));
        MODELS.put(PartType.LEG, Models.box(16, 16, -1.5f, 0, -1.5f, 3, 10, 3));
        MODELS.put(PartType.TORSO, Models.box(16, 16, -3, -3, -2, 6, 6, 4));
        MODELS.put(PartType.TAIL, Models.box(0, 0, -1, 0, -1, 2, 11, 2));
        MODELS.put(PartType.EYE, Models.box(0, 0, -2, -2, -2, 4, 4, 4));
        MODELS.put(PartType.JAW, Models.box(0, 0, -3, 0, -2, 6, 2, 4));
    }

    public DevourFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity pl,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        PlayerDevour d = ClientData.DATA.get(pl.getUuid());
        if (d == null || d.parts.isEmpty() || pl.isInvisible()) return;
        float t = pl.age + tickDelta;

        for (DevourPart part : d.parts) {
            Random r = new Random(part.seed());
            float side = r.nextBoolean() ? -1f : 1f;
            float px, py, pz, rx, ry, rz;
            switch (part.type()) {
                case HEAD, EYE, JAW -> {
                    px = (r.nextFloat() - 0.5f) * 7f;
                    py = 1f + r.nextFloat() * 8f;
                    pz = -2.4f;
                    rx = (r.nextFloat() - 0.5f) * 0.8f;
                    ry = (r.nextFloat() - 0.5f) * 1.4f;
                    rz = (r.nextFloat() - 0.5f) * 0.8f;
                }
                default -> {
                    px = side * (4f + r.nextFloat() * 2.5f);
                    py = r.nextFloat() * 11f;
                    pz = (r.nextFloat() - 0.5f) * 5f;
                    rz = -side * (0.4f + r.nextFloat() * 1.9f);
                    rx = (r.nextFloat() - 0.5f) * 1.6f;
                    ry = (r.nextFloat() - 0.5f) * 1.2f;
                }
            }
            // movimento orgânico: a parte ainda "se contorce"
            float wig = (float) Math.sin(t * 0.12f + (part.seed() & 255)) * 0.09f;
            rz += wig;
            rx += wig * 0.7f;

            m.push();
            m.translate(px / 16f, py / 16f, pz / 16f);
            m.multiply(RotationAxis.POSITIVE_Z.rotation(rz));
            m.multiply(RotationAxis.POSITIVE_Y.rotation(ry));
            m.multiply(RotationAxis.POSITIVE_X.rotation(rx));
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(texture(part.species())));
            MODELS.get(part.type()).render(m, vc, light, OverlayTexture.DEFAULT_UV, 0.78f, 0.6f, 0.6f, 1f);
            m.pop();
        }
    }

    /** Pega a textura real da criatura (cria uma instância temporária só para consultar o renderer). */
    @SuppressWarnings("unchecked")
    private static Identifier texture(String species) {
        Identifier cached = TEXTURES.get(species);
        if (cached != null) return cached;
        Identifier result = FALLBACK;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(species));
            Entity e = type.create(mc.world);
            if (e != null) {
                EntityRenderer<Entity> er = (EntityRenderer<Entity>) (EntityRenderer<?>) mc.getEntityRenderDispatcher().getRenderer(e);
                result = er.getTexture(e);
            }
        } catch (Throwable ignored) {
        }
        TEXTURES.put(species, result);
        return result;
    }
}
