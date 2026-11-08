package com.devourer.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Catálogo de habilidades, lido de assets/devourer/abilities.json (edite o JSON para mudar textos). */
public final class AbilityData {
    public record Category(String id, String name) {
    }

    public record Ability(String id, String cat, String name, String origin, String desc, String anim,
                          String fx, String gameplay, List<String> evo, List<String> mobs) {
    }

    public final List<Category> categories = new ArrayList<>();
    public final List<Ability> abilities = new ArrayList<>();
    public String error = null;

    public static AbilityData load() {
        AbilityData d = new AbilityData();
        try {
            Resource res = MinecraftClient.getInstance().getResourceManager()
                    .getResource(new Identifier("devourer", "abilities.json")).orElseThrow();
            try (Reader rd = res.getReader()) {
                JsonObject root = JsonParser.parseReader(rd).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("categories")) {
                    JsonObject o = e.getAsJsonObject();
                    d.categories.add(new Category(o.get("id").getAsString(), o.get("name").getAsString()));
                }
                for (JsonElement e : root.getAsJsonArray("abilities")) {
                    JsonObject o = e.getAsJsonObject();
                    d.abilities.add(new Ability(
                            str(o, "id"), str(o, "cat"), str(o, "name"), str(o, "origin"), str(o, "desc"),
                            str(o, "anim"), str(o, "fx"), str(o, "game"), list(o, "evo"), list(o, "mobs")));
                }
            }
        } catch (Throwable t) {
            d.error = "Não foi possível ler abilities.json: " + t;
        }
        return d;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "";
    }

    private static List<String> list(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k)) {
            JsonArray a = o.getAsJsonArray(k);
            for (JsonElement e : a) out.add(e.getAsString());
        }
        return out;
    }
}
