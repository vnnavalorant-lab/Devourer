package com.devourer.tentacle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lê os mobs fornecedores de cada habilidade direto de abilities.json (fonte única, também usada pela tela K). */
public final class Catalog {
    private static Map<String, List<String>> MOBS;

    public static synchronized List<String> mobs(String abilityId) {
        if (MOBS == null) {
            MOBS = new HashMap<>();
            try (InputStream in = Catalog.class.getResourceAsStream("/assets/devourer/abilities.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : root.getAsJsonArray("abilities")) {
                        JsonObject o = e.getAsJsonObject();
                        List<String> list = new ArrayList<>();
                        if (o.has("mobs")) for (JsonElement m : o.getAsJsonArray("mobs")) list.add(m.getAsString());
                        MOBS.put(o.get("id").getAsString(), list);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return MOBS.getOrDefault(abilityId, List.of());
    }

    /**
     * Nível 0..3 = quantos mobs DIFERENTES da lista da habilidade o jogador tem presos no corpo.
     * 0 = habilidade bloqueada. (Nv1 = 1 mob da lista, Nv2 = 2, Nv3 = 3.)
     */
    public static int level(com.devourer.PlayerDevour d, TentacleAbility a) {
        List<String> need = mobs(a.id);
        int n = 0;
        for (String m : need) {
            for (com.devourer.DevourPart p : d.parts) {
                if (p.species().equals(m)) {
                    n++;
                    break;
                }
            }
        }
        return Math.min(3, n);
    }

    private Catalog() {
    }
}
