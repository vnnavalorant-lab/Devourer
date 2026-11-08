package com.devourer.client.render;

import com.devourer.DevourerMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo "zombie_form" (gerado por tools/convert_bbmodel.py a partir do .bbmodel):
 * bones com quads já assados (posição em px, UV normalizado) + correntes de tentáculos (marcador "ik").
 */
public final class ZombieFormModel {
    public static final class Quad {
        public int tex;
        public final float[] p = new float[12];
        public final float[] uv = new float[8];
        public float nx, ny, nz;
    }

    public static final class Bone {
        public String name;
        public int parent;
        public float px, py, pz;
        public final Quaternionf staticRot = new Quaternionf();
        public final List<Quad> quads = new ArrayList<>();
        public final List<Integer> children = new ArrayList<>();
    }

    /** Corrente de tentáculo: bones do tentacle* até antes do marcador ik*. */
    public static final class Chain {
        public int[] bones;
        public float[] len;
        public float total;
        public final Vector3f axis = new Vector3f();
        public float phase;
    }

    public final List<Bone> bones = new ArrayList<>();
    public final List<Integer> roots = new ArrayList<>();
    public final List<Chain> chains = new ArrayList<>();
    public Identifier[] textures;

    private static ZombieFormModel INSTANCE;

    public static synchronized ZombieFormModel get() {
        if (INSTANCE == null) {
            try {
                INSTANCE = load();
            } catch (Exception e) {
                throw new RuntimeException("Falha ao carregar zombie_form.json", e);
            }
        }
        return INSTANCE;
    }

    private static ZombieFormModel load() throws Exception {
        ZombieFormModel m = new ZombieFormModel();
        try (InputStream in = ZombieFormModel.class.getResourceAsStream("/assets/devourer/models/zombie_form.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray tex = root.getAsJsonArray("textures");
            m.textures = new Identifier[tex.size()];
            for (int i = 0; i < tex.size(); i++) {
                m.textures[i] = new Identifier(DevourerMod.ID, "textures/entity/" + tex.get(i).getAsString() + ".png");
            }

            for (JsonElement be : root.getAsJsonArray("bones")) {
                JsonObject bo = be.getAsJsonObject();
                Bone b = new Bone();
                b.name = bo.get("name").getAsString();
                b.parent = bo.get("parent").getAsInt();
                JsonArray pv = bo.getAsJsonArray("pivot");
                b.px = pv.get(0).getAsFloat();
                b.py = pv.get(1).getAsFloat();
                b.pz = pv.get(2).getAsFloat();
                JsonArray r = bo.getAsJsonArray("rot");
                b.staticRot.rotationZYX((float) Math.toRadians(r.get(2).getAsFloat()),
                        (float) Math.toRadians(r.get(1).getAsFloat()),
                        (float) Math.toRadians(r.get(0).getAsFloat()));
                for (JsonElement qe : bo.getAsJsonArray("quads")) {
                    JsonObject qo = qe.getAsJsonObject();
                    Quad q = new Quad();
                    q.tex = qo.get("t").getAsInt();
                    JsonArray p = qo.getAsJsonArray("p");
                    for (int i = 0; i < 12; i++) q.p[i] = p.get(i).getAsFloat();
                    JsonArray uv = qo.getAsJsonArray("uv");
                    for (int i = 0; i < 8; i++) q.uv[i] = uv.get(i).getAsFloat();
                    JsonArray n = qo.getAsJsonArray("n");
                    q.nx = n.get(0).getAsFloat();
                    q.ny = n.get(1).getAsFloat();
                    q.nz = n.get(2).getAsFloat();
                    b.quads.add(q);
                }
                // agrupa por textura para minimizar trocas de RenderLayer
                b.quads.sort((a, c) -> Integer.compare(a.tex, c.tex));
                m.bones.add(b);
            }
        }

        for (int i = 0; i < m.bones.size(); i++) {
            Bone b = m.bones.get(i);
            if (b.parent < 0) m.roots.add(i);
            else m.bones.get(b.parent).children.add(i);
        }

        // correntes de tentáculo: cada marcador "ik*" sobe até o bone "tentacle*"
        int n = 0;
        for (int i = 0; i < m.bones.size(); i++) {
            if (!m.bones.get(i).name.startsWith("ik")) continue;
            List<Integer> path = new ArrayList<>();
            int cur = m.bones.get(i).parent;
            while (cur >= 0) {
                path.add(0, cur);
                if (m.bones.get(cur).name.startsWith("tentacle")) break;
                cur = m.bones.get(cur).parent;
            }
            if (path.isEmpty() || !m.bones.get(path.get(0)).name.startsWith("tentacle")) continue;

            Chain c = new Chain();
            c.bones = path.stream().mapToInt(Integer::intValue).toArray();
            c.len = new float[c.bones.length];
            for (int k = 0; k < c.bones.length; k++) {
                Bone a = m.bones.get(c.bones[k]);
                Bone nx = k + 1 < c.bones.length ? m.bones.get(c.bones[k + 1]) : m.bones.get(i);
                c.len[k] = (float) Math.sqrt(Math.pow(nx.px - a.px, 2) + Math.pow(nx.py - a.py, 2) + Math.pow(nx.pz - a.pz, 2));
                c.total += c.len[k];
            }
            Bone root = m.bones.get(c.bones[0]);
            Bone tip = m.bones.get(i);
            c.axis.set(tip.px - root.px, tip.py - root.py, tip.pz - root.pz).normalize();
            c.phase = n * 2.1f + 0.7f;
            m.chains.add(c);
            n++;
        }
        return m;
    }

    private ZombieFormModel() {
    }
}
