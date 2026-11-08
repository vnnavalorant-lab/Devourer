package com.devourer.client.render;

import com.devourer.client.render.ZombieFormModel.Bone;
import com.devourer.client.render.ZombieFormModel.Chain;
import com.devourer.client.render.ZombieFormModel.Quad;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.devourer.client.ClientTentacles;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderiza o modelo zombie_form POR CIMA da skin original do jogador.
 * - Head/Body/Arms/Legs seguem os ModelParts do jogador (andar, mirar, agachar...).
 * - Os tentáculos usam FABRIK (IK) em direção a um alvo animado, então dobram e se mexem de verdade.
 * - Anti Z-fighting: usa a RenderLayer das armaduras (offset de profundidade em direção à câmera), então
 *   faces coplanares com o corpo vanilla sempre vencem, sem deformar as camadas finas do modelo.
 */
public class ZombieFormFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final Map<UUID, Vector3f> drag = new HashMap<>();
    /** Quanto um tentáculo pode se esticar (x o comprimento normal) para alcançar alvos distantes das habilidades. */
    private static final float MAX_STRETCH = 7f;
    /** Eixo de cada bone de tentáculo (para esticar a geometria ao longo dele). */
    private Vector3f[] axisOf;

    // ---- pupila ("pupil"): segue a câmera sem sair da janela da casca do focinho ----
    // A janela da casca tem 6x6px e a pupila 4x4px (centrada). Pelo desenho da textura, a borda de cima é opaca
    // e só os cantos de baixo são opacos, então a pupila pode ir 1px para os lados OU 1px para baixo (não para cima).
    private static final float PUPIL_MAX_X = 1.0f;     // px para os lados
    private static final float PUPIL_MAX_DOWN = 1.0f;  // px para baixo
    private static final float PUPIL_MAX_UP = 0.0f;    // px para cima (a borda opaca de cima impede)
    private static final float PUPIL_GAIN = 1.4f;      // sensibilidade: atinge o limite em ~45° de ângulo
    private static final float PUPIL_SMOOTH = 0.3f;    // suavização por frame
    private final Map<UUID, float[]> gaze = new HashMap<>();


    public ZombieFormFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity pl,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (pl.isInvisible() || pl.isSpectator() || !com.devourer.client.ClientData.isInfected(pl.getUuid())) return;
        ZombieFormModel model = ZombieFormModel.get();
        float t = pl.age + tickDelta;

        // inércia: os tentáculos ficam "para trás" do movimento
        Vector3f d = drag.computeIfAbsent(pl.getUuid(), k -> new Vector3f());
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, pl.prevBodyYaw, pl.bodyYaw);
        Vector3f w = new Vector3f((float) (pl.getX() - pl.prevX), (float) (pl.getY() - pl.prevY), (float) (pl.getZ() - pl.prevZ));
        w.rotateY(-(float) Math.toRadians(180f - bodyYaw));
        w.x = -w.x;
        w.y = -w.y;
        Vector3f target = w.mul(-45f);
        if (target.length() > 14f) target.normalize(14f);
        d.lerp(target, 0.12f);

        float agitation = Math.min(1.5f, limbDistance * 2f);
        Quaternionf[] ik = new Quaternionf[model.bones.size()];
        float[] stretch = new float[model.bones.size()];
        java.util.Arrays.fill(stretch, 1f);
        if (axisOf == null || axisOf.length != model.bones.size()) {
            axisOf = new Vector3f[model.bones.size()];
            for (Chain c : model.chains) for (int b : c.bones) axisOf[b] = c.axis;
        }

        // habilidades: o servidor manda o alvo (no mundo) de cada tentáculo; sem dados, animação normal
        ClientTentacles.View view = ClientTentacles.get(pl.getUuid(), pl.getWorld().getTime());
        Vec3d ep = pl.getLerpedPos(tickDelta);
        for (int ci = 0; ci < model.chains.size(); ci++) {
            Chain c = model.chains.get(ci);
            Vector3f forced = null;
            float ag = agitation;
            boolean nervous = false;
            if (view != null && ci < 4) {
                switch (view.mode[ci]) {
                    case 1 -> { // alvo no mundo (vítima, ponto da parede, projétil...)
                        Vec3d tg = view.target[ci];
                        if (tg != null) {
                            Vec3d from = view.cur[ci] != null ? view.cur[ci] : ep.add(0, 1.3, 0);
                            Vec3d cur = from.lerp(tg, 0.4); // o alvo "salta" em poucos frames em vez de teleportar
                            view.cur[ci] = cur;
                            forced = worldToChain(model, c, cur, ep, bodyYaw);
                            ag = 1.5f;
                        }
                    }
                    case 2 -> { // Raízes: deitado no chão, serpenteando ao redor do jogador
                        double ang = ci * Math.PI / 2 + Math.PI / 4 + Math.sin(t * 0.04 + ci) * 0.5;
                        double rad = 1.2 + 0.35 * Math.sin(t * 0.09 + ci * 1.3);
                        Vec3d g = new Vec3d(ep.x + Math.cos(ang) * rad, ep.y + 0.05, ep.z + Math.sin(ang) * rad);
                        forced = worldToChain(model, c, g, ep, bodyYaw);
                        ag = 0.6f;
                    }
                    case 3 -> { // Inquietação: tremores e espasmos
                        nervous = true;
                        ag = 1.5f;
                    }
                    default -> {
                    }
                }
            }
            float k = solve(model, c, t, ag, d, ik, forced, nervous);
            for (int b : c.bones) stretch[b] = k;
        }

        int overlay = LivingEntityRenderer.getOverlay(pl, 0.0f);
        for (int root : model.roots) renderBone(model, root, m, vcp, light, overlay, ik, stretch, pl.getUuid());
    }

    // ------------------------------------------------------------------ render

    private ModelPart vanilla(String name) {
        PlayerEntityModel<AbstractClientPlayerEntity> pm = getContextModel();
        return switch (name) {
            case "Head" -> pm.head;
            case "Body" -> pm.body;
            case "Right Arm" -> pm.rightArm;
            case "Left Arm" -> pm.leftArm;
            case "Left Leg" -> pm.leftLeg;
            case "Right Leg" -> pm.rightLeg;
            default -> null;
        };
    }

    private void renderBone(ZombieFormModel model, int idx, MatrixStack m, VertexConsumerProvider vcp,
                            int light, int overlay, Quaternionf[] ik, float[] stretch, UUID id) {
        Bone b = model.bones.get(idx);
        m.push();
        ModelPart v = b.parent < 0 ? vanilla(b.name) : null;
        if (v != null) {
            v.rotate(m); // pivô atual + rotação animada do jogador
        } else if (b.parent < 0) {
            m.translate(b.px / 16f, b.py / 16f, b.pz / 16f);
            m.multiply(b.staticRot);
        } else {
            Bone p = model.bones.get(b.parent);
            float ks = stretch[b.parent]; // filho de um segmento esticado fica mais longe
            m.translate((b.px - p.px) * ks / 16f, (b.py - p.py) * ks / 16f, (b.pz - p.pz) * ks / 16f);
            m.multiply(b.staticRot);
        }
        if (ik[idx] != null) m.multiply(ik[idx]);
        if (b.name.startsWith("pupil")) applyPupilGaze(m, id);

        if (!b.quads.isEmpty()) {
            MatrixStack.Entry e = m.peek();
            Matrix4f pm = e.getPositionMatrix();
            Matrix3f nm = e.getNormalMatrix();
            int cur = -1;
            VertexConsumer vc = null;
            float sk = stretch[idx];
            Vector3f ax = sk > 1.001f ? axisOf[idx] : null;
            float thin = 1f / (float) Math.sqrt(sk); // esticou: afina, para não virar um cano grosso
            for (Quad q : b.quads) {
                if (q.tex != cur) {
                    cur = q.tex;
                    vc = vcp.getBuffer(RenderLayer.getArmorCutoutNoCull(model.textures[cur]));
                }
                for (int k = 0; k < 4; k++) {
                    float vx = q.p[k * 3] / 16f, vy = q.p[k * 3 + 1] / 16f, vz = q.p[k * 3 + 2] / 16f;
                    if (ax != null) {
                        float al = vx * ax.x + vy * ax.y + vz * ax.z;
                        float qx = vx - ax.x * al, qy = vy - ax.y * al, qz = vz - ax.z * al;
                        vx = qx * thin + ax.x * al * sk;
                        vy = qy * thin + ax.y * al * sk;
                        vz = qz * thin + ax.z * al * sk;
                    }
                    vc.vertex(pm, vx, vy, vz)
                            .color(1f, 1f, 1f, 1f)
                            .texture(q.uv[k * 2], q.uv[k * 2 + 1])
                            .overlay(overlay)
                            .light(light)
                            .normal(nm, q.nx, q.ny, q.nz)
                            .next();
                }
            }
        }
        for (int c : b.children) renderBone(model, c, m, vcp, light, overlay, ik, stretch, id);
        m.pop();
    }

    /**
     * Desloca a pupila (só translação no plano da pupila; nenhuma rotação) na direção da câmera.
     * A posição da câmera no espaço local do bone sai da inversa da matriz atual (a câmera é a origem do espaço de view).
     */
    private void applyPupilGaze(MatrixStack m, UUID id) {
        Vector3f cam = new Matrix4f(m.peek().getPositionMatrix()).invert().transformPosition(new Vector3f());
        float len = cam.length();
        float tx = 0f, ty = 0f;
        if (len > 1e-4f) {
            tx = MathHelper.clamp(cam.x / len * PUPIL_GAIN, -1f, 1f);
            ty = MathHelper.clamp(cam.y / len * PUPIL_GAIN, -1f, 1f); // +y local = para baixo
        }
        // sem sobreposição com a casca: move-se no eixo dominante (lado OU baixo)
        if (Math.abs(tx) >= Math.max(0f, ty)) ty = 0f;
        else tx = 0f;
        float goalX = tx * PUPIL_MAX_X;
        float goalY = ty >= 0 ? ty * PUPIL_MAX_DOWN : ty * PUPIL_MAX_UP;

        float[] cur = gaze.computeIfAbsent(id, k -> new float[2]);
        cur[0] += (goalX - cur[0]) * PUPIL_SMOOTH;
        cur[1] += (goalY - cur[1]) * PUPIL_SMOOTH;
        cur[0] = MathHelper.clamp(cur[0], -PUPIL_MAX_X, PUPIL_MAX_X);
        cur[1] = MathHelper.clamp(cur[1], -PUPIL_MAX_UP, PUPIL_MAX_DOWN);
        m.translate(cur[0] / 16f, cur[1] / 16f, 0f);
    }

    // ------------------------------------------------------------------ IK (FABRIK)

    /** Ponto do mundo -> espaço local da raiz do tentáculo (em px). Ignora a inclinação do corpo ao agachar. */
    private static Vector3f worldToChain(ZombieFormModel model, Chain c, Vec3d world, Vec3d entityPos, float bodyYaw) {
        Vector3f w = new Vector3f((float) (world.x - entityPos.x), (float) (world.y - entityPos.y), (float) (world.z - entityPos.z));
        w.rotateY(-(float) Math.toRadians(180f - bodyYaw)); // mesma conversão usada para a inércia acima
        w.x = -w.x;
        w.y = -w.y;
        w.y += 1.501f; // origem do modelo fica 1.501 bloco acima dos pés, com +y para baixo
        w.mul(16f);
        Bone root = model.bones.get(c.bones[0]);
        w.sub(root.px, root.py, root.pz);
        return w.rotate(new Quaternionf(root.staticRot).conjugate());
    }

    /**
     * Resolve a corrente. {@code forced} (espaço local da raiz, px) substitui o alvo animado; se estiver além do
     * alcance, o tentáculo se estica (até MAX_STRETCH x) e o fator de esticamento é devolvido.
     */
    private static float solve(ZombieFormModel model, Chain c, float t, float agitation, Vector3f drag, Quaternionf[] out,
                               Vector3f forced, boolean nervous) {
        int n = c.bones.length;
        Bone rootBone = model.bones.get(c.bones[0]);
        float ph = c.phase;

        float k = 1f;
        Vector3f target;
        if (forced != null) {
            target = new Vector3f(forced);
            k = MathHelper.clamp(target.length() / c.total, 1f, MAX_STRETCH);
        } else {
            // alvo no espaço local da raiz do tentáculo (a ponta "ik" tenta alcançá-lo)
            float amp = 6f + 6f * agitation;
            Vector3f local = new Vector3f(drag).rotate(new Quaternionf(rootBone.staticRot).conjugate());
            target = new Vector3f(c.axis).mul(c.total * 0.72f)
                    .add(amp * (float) Math.sin(t * 0.11f + ph),
                            amp * 0.55f * (float) Math.sin(t * 0.07f + ph * 1.7f + 1f),
                            amp * (float) Math.cos(t * 0.09f + ph * 0.6f))
                    .add(local);
            if (nervous) {
                target.add(7f * (float) Math.sin(t * 0.9f + ph * 3f), 5f * (float) Math.sin(t * 1.3f + ph),
                        7f * (float) Math.cos(t * 1.1f + ph * 2f));
            }
        }
        float total = c.total * k;
        float[] len = new float[n];
        for (int i = 0; i < n; i++) len[i] = c.len[i] * k;

        Vector3f[] p = new Vector3f[n + 1];
        p[0] = new Vector3f();
        for (int i = 0; i < n; i++) p[i + 1] = new Vector3f(c.axis).mul(len[i]).add(p[i]);

        if (target.length() >= total) {
            target.normalize(total);
            for (int i = 0; i < n; i++) p[i + 1].set(p[i]).fma(len[i], new Vector3f(target).normalize());
        } else {
            for (int it = 0; it < 8; it++) {
                p[n].set(target);
                for (int i = n - 1; i >= 0; i--) pull(p[i], p[i + 1], len[i]);
                p[0].zero();
                for (int i = 0; i < n; i++) pull(p[i + 1], p[i], len[i]);
            }
        }

        Quaternionf prev = new Quaternionf();
        for (int i = 0; i < n; i++) {
            Vector3f dir = new Vector3f(p[i + 1]).sub(p[i]);
            if (dir.lengthSquared() < 1e-8f) dir.set(c.axis);
            dir.normalize();
            Vector3f vLocal = new Vector3f(dir).rotate(new Quaternionf(prev).conjugate());
            Quaternionf l = new Quaternionf().rotationTo(c.axis, vLocal);
            // ondulação secundária: o movimento viaja da base até a ponta
            float wave = (float) Math.sin(t * 0.21f - i * 0.9f + ph) * 0.10f * (0.6f + agitation);
            float wave2 = (float) Math.cos(t * 0.17f - i * 0.7f + ph) * 0.08f * (0.6f + agitation);
            l.mul(new Quaternionf().rotationXYZ(wave, 0f, wave2));
            out[c.bones[i]] = l;
            prev.mul(l);
        }
        return k;
    }

    /** Move {@code a} para ficar a {@code len} de {@code b}, na mesma direção. */
    private static void pull(Vector3f a, Vector3f b, float len) {
        Vector3f d = new Vector3f(a).sub(b);
        if (d.lengthSquared() < 1e-8f) d.set(0, -1, 0);
        d.normalize(len);
        a.set(b).add(d);
    }
}
