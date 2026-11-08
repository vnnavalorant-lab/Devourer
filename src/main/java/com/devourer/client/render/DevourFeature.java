package com.devourer.client.render;

import com.devourer.DevourPart;
import com.devourer.Mount;
import com.devourer.PlayerDevour;
import com.devourer.client.ClientData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Desenha os membros arrancados de outros mobs presos ao corpo do jogador.
 *
 * Os membros são os ModelParts REAIS do modelo do mob (braço, perna, cauda...), tirados da camada de modelo
 * do próprio mob, e desenhados com a textura que o renderer desse mob usa. Assim o UV e a textura ficam corretos.
 *
 * Posições: pontos fixos de {@link Mount} espalhados por costas, ombros, laterais do tronco, braços e pernas. Nunca a cabeça.
 */
public class DevourFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private enum Kind {ARM, LEG, OTHER}

    /** Um membro de mob pronto para desenhar (pivô zerado, apontando para baixo a partir da junta). */
    private record Limb(ModelPart part, Kind kind, float scale) {
    }

    private record MobLimbs(Identifier texture, List<Limb> limbs) {
    }

    private static final Identifier ZOMBIE = new Identifier("minecraft:zombie");
    private static final Identifier ZOMBIE_TEXTURE = new Identifier("textures/entity/zombie/zombie.png");
    private static final Map<String, MobLimbs> CACHE = new HashMap<>();

    // Nomes de filhos da raiz do modelo (iguais nos modelos vanilla). A cabeça e seus adereços ficam de fora de propósito.
    private static final String[] ARMS = {"right_arm", "left_arm", "arms"};
    private static final String[] LEGS = {
            "right_leg", "left_leg",
            "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg",
            "right_middle_hind_leg", "left_middle_hind_leg", "right_middle_front_leg", "left_middle_front_leg"
    };
    private static final String[] OTHERS = {
            "tail", "tail_base", "right_wing", "left_wing",
            "tentacle0", "tentacle1", "tentacle2", "tentacle3", "tentacle4", "tentacle5", "tentacle6", "tentacle7"
    };
    /** Só usados se o mob não tiver nenhum dos membros acima (slime, por exemplo). */
    private static final String[] BODY_FALLBACK = {"cube", "body"};

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
        int overlay = LivingEntityRenderer.getOverlay(pl, 0.0f);
        PlayerEntityModel<AbstractClientPlayerEntity> pm = getContextModel();

        for (DevourPart part : d.parts) {
            MobLimbs mob = resolve(part.species());
            if (mob.limbs().isEmpty()) continue;

            // sorteios em ordem fixa: a mesma seed sempre dá o mesmo membro e a mesma variação
            Random r = new Random(part.seed());
            float rPick = r.nextFloat();
            float rA = r.nextFloat(), rB = r.nextFloat(), rC = r.nextFloat();

            Mount mt = part.mount();
            Limb limb = pick(mob.limbs(), mt, rPick);
            float wig = (float) Math.sin(t * 0.12f + (part.seed() & 255)) * 0.09f;

            m.push();
            switch (mt.attach) {
                case BODY -> pm.body.rotate(m);
                case ARM_R -> pm.rightArm.rotate(m);
                case ARM_L -> pm.leftArm.rotate(m);
                case LEG_R -> pm.rightLeg.rotate(m);
                case LEG_L -> pm.leftLeg.rotate(m);
            }
            // ponto fixo do corpo + pequena variação por seed, para duplicados não ficarem idênticos
            m.translate(mt.x / 16f, (mt.y + (rA - 0.5f)) / 16f, mt.z / 16f);
            m.multiply(RotationAxis.POSITIVE_Z.rotation(mt.rz + (rB - 0.5f) * 0.25f + wig));
            m.multiply(RotationAxis.POSITIVE_X.rotation(mt.rx + (rC - 0.5f) * 0.25f + wig * 0.7f));
            m.scale(limb.scale(), limb.scale(), limb.scale());

            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(mob.texture()));
            limb.part().render(m, vc, light, overlay, 1f, 1f, 1f, 1f);
            m.pop();
        }
    }

    /** Braços preferem braços do mob, pernas preferem pernas; no corpo vale qualquer membro. */
    private static Limb pick(List<Limb> limbs, Mount mount, float r) {
        Kind want = switch (mount.attach) {
            case ARM_R, ARM_L -> Kind.ARM;
            case LEG_R, LEG_L -> Kind.LEG;
            default -> null;
        };
        List<Limb> pool = limbs;
        if (want != null) {
            List<Limb> filtered = new ArrayList<>();
            for (Limb l : limbs) if (l.kind() == want) filtered.add(l);
            if (!filtered.isEmpty()) pool = filtered;
        }
        return pool.get(MathHelper.clamp((int) (r * pool.size()), 0, pool.size() - 1));
    }

    // ------------------------------------------------------------------ carregamento dos modelos

    private static MobLimbs resolve(String species) {
        MobLimbs c = CACHE.get(species);
        if (c != null) return c;
        MobLimbs loaded = load(new Identifier(species));
        if (loaded.limbs().isEmpty()) loaded = load(ZOMBIE); // espécie sem modelo conhecido: usa o zumbi
        CACHE.put(species, loaded);
        return loaded;
    }

    @SuppressWarnings("unchecked")
    private static MobLimbs load(Identifier id) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // textura: a que o renderer do próprio mob usa
        Identifier texture = ZOMBIE_TEXTURE;
        try {
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);
            Entity e = type.create(mc.world);
            if (e != null) {
                EntityRenderer<Entity> er = (EntityRenderer<Entity>) (EntityRenderer<?>) mc.getEntityRenderDispatcher().getRenderer(e);
                texture = er.getTexture(e);
            }
        } catch (Throwable ignored) {
        }

        // membros: partes do modelo do mob (camada "main", que na maioria dos mobs tem o mesmo id da espécie)
        List<Limb> limbs = new ArrayList<>();
        try {
            ModelPart root = mc.getEntityModelLoader().getModelPart(new EntityModelLayer(layerId(id), "main"));
            collect(root, ARMS, Kind.ARM, limbs);
            collect(root, LEGS, Kind.LEG, limbs);
            collect(root, OTHERS, Kind.OTHER, limbs);
            if (limbs.isEmpty()) collect(root, BODY_FALLBACK, Kind.OTHER, limbs);
        } catch (Throwable ignored) {
        }
        return new MobLimbs(texture, limbs);
    }

    /** Espécies que reaproveitam o modelo de outra. */
    private static Identifier layerId(Identifier species) {
        return switch (species.getPath()) {
            case "cave_spider" -> new Identifier("minecraft:spider");
            default -> species;
        };
    }

    private static void collect(ModelPart root, String[] names, Kind kind, List<Limb> out) {
        for (String name : names) {
            ModelPart p;
            try {
                p = root.getChild(name);
            } catch (Throwable e) {
                continue; // este mob não tem esse membro
            }
            p.setTransform(ModelTransform.NONE); // pivô na junta e sem rotação: o membro pende para baixo
            out.add(new Limb(p, kind, scaleFor(p)));
        }
    }

    /** Ajusta o tamanho para o membro ter ~11px de alcance (o braço do jogador tem 12), sem exagerar. */
    private static float scaleFor(ModelPart p) {
        float[] reach = {0f};
        p.forEachCuboid(new MatrixStack(), (entry, path, index, c) -> {
            float r = Math.max(Math.max(Math.max(Math.abs(c.minX), Math.abs(c.maxX)), Math.max(Math.abs(c.minY), Math.abs(c.maxY))),
                    Math.max(Math.abs(c.minZ), Math.abs(c.maxZ)));
            if (r > reach[0]) reach[0] = r;
        });
        if (reach[0] < 0.5f) return 1f;
        return MathHelper.clamp(11f / reach[0], 0.4f, 1.25f);
    }
}
