package com.devourer.client.gui;

import com.devourer.DevourPart;
import com.devourer.PlayerDevour;
import com.devourer.client.ClientData;
import com.devourer.client.gui.AbilityData.Ability;
import com.devourer.client.gui.AbilityData.Category;
import com.devourer.tentacle.Catalog;
import com.devourer.tentacle.TentacleAbility;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela das habilidades funcionais: a lista fica DENTRO da parte escura da GUI (textura HD tentacle_gui.png)
 * e os detalhes da habilidade selecionada aparecem no painel à direita.
 * Roda do mouse rola o texto; setas cima/baixo trocam a habilidade selecionada.
 */
public class AbilitiesScreen extends Screen {
    private static final Identifier GUI_TEX = new Identifier("devourer", "textures/gui/tentacle_gui.png");
    private static final int TEX_SIZE = 91;    // arte original, desenhada em múltiplos inteiros (nítida)
    private static final int IMG_PX = 91;      // lado da arte original, usado nas coordenadas abaixo

    // Faixas de texto dentro da parte escura da boca (coordenadas na arte de 91x91): início Y, altura e limites X de cada linha.
    private static final float BAND_Y0 = 30f, BAND_H = 3.3f;
    private static final float[] BAND_L = {18.5f, 19.5f, 21.5f, 23.5f, 26.5f, 29.5f, 35.5f, 38.5f, 44.5f};
    private static final float[] BAND_R = {47.5f, 47.5f, 49.5f, 51.5f, 55.5f, 55.5f, 61.5f, 67.5f, 67.5f};

    private static final int LINE_H = 10;
    private static final int GREEN = 0xFF55FF55;

    private record Line(OrderedText text, int color) {
    }

    private final AbilityData data = AbilityData.load();
    private final Set<String> owned = new HashSet<>();
    private PlayerDevour mine = new PlayerDevour();

    private List<Ability> shown = new ArrayList<>();
    private int selected = 0;
    private double detailScroll = 0;
    private final List<Line> lines = new ArrayList<>();

    private int guiX, guiY, guiSize, detX, detY, detW, detH;

    public AbilitiesScreen() {
        super(Text.literal("Habilidades mutantes"));
    }

    @Override
    protected void init() {
        owned.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            PlayerDevour d = ClientData.DATA.get(mc.player.getUuid());
            if (d != null) {
                mine = d;
                for (DevourPart p : d.parts) owned.add(p.species());
            }
        }

        // filtro 'nearest': pixel art nítida, sem borrar
        try {
            mc.getTextureManager().getTexture(GUI_TEX).setFilter(false, false);
        } catch (Throwable ignored) {
        }

        int top = 32;
        int maxSize = Math.min(height - top - 8, (int) (width * 0.62));
        guiSize = IMG_PX * Math.max(1, maxSize / IMG_PX); // múltiplo inteiro: cada pixel da arte vira N px exatos
        guiX = 8;
        guiY = top + Math.max(0, (height - top - 8 - guiSize) / 2);
        detX = guiX + guiSize + 6;
        detY = top;
        detW = Math.max(60, width - detX - 8);
        detH = height - top - 8;

        rebuildShown();
        rebuildLines();
    }

    private void rebuildShown() {
        shown = new ArrayList<>(data.abilities);
        selected = MathHelper.clamp(selected, 0, Math.max(0, shown.size() - 1));
    }

    // ------------------------------------------------------------------ disponibilidade

    /** Combinadas exigem todos os mobs; as demais, qualquer um. */
    private boolean available(Ability a) {
        if (a.mobs().isEmpty()) return false;
        boolean all = false;
        int have = 0;
        for (String m : a.mobs()) if (owned.contains(m)) have++;
        return all ? have == a.mobs().size() : have > 0;
    }

    private static TentacleAbility functional(Ability a) {
        for (TentacleAbility t : TentacleAbility.ALL) if (t.id.equals(a.id())) return t;
        return null;
    }

    private static String mobName(String id) {
        try {
            Identifier i = new Identifier(id);
            if (Registries.ENTITY_TYPE.containsId(i)) return Registries.ENTITY_TYPE.get(i).getName().getString();
        } catch (Throwable ignored) {
        }
        return id;
    }

    // ------------------------------------------------------------------ texto dos detalhes

    private void rebuildLines() {
        lines.clear();
        detailScroll = 0;
        if (data.error != null) {
            wrap(data.error, 0xFFFF5555);
            return;
        }
        if (shown.isEmpty()) return;
        Ability a = shown.get(selected);

        wrap(a.name(), 0xFFFFD166);
        String catName = "";
        for (Category c : data.categories) if (c.id().equals(a.cat())) catName = c.name();
        wrap(catName, 0xFF888888);

        List<String> names = new ArrayList<>();
        for (String m : a.mobs()) names.add(mobName(m) + (owned.contains(m) ? " ✔" : ""));
        String all = " (qualquer um)";
        if (available(a)) wrap("Você tem os membros necessários no corpo.", GREEN);
        else wrap("Ainda não disponível: devore " + String.join(", ", names) + all + ".", 0xFFFF8855);

        TentacleAbility fn = functional(a);
        if (fn != null) {
            int lvl = Catalog.level(mine, fn);
            String how = switch (fn.mode) {
                case HOLD -> "segure a tecla de uso";
                case PRESS -> "aperte a tecla de uso";
                case TOGGLE -> "aperte a tecla de uso para ligar/desligar";
            };
            wrap("Nível atual: " + lvl + "/3 (cada mob diferente da lista abaixo que você devorou sobe 1 nível). "
                    + "Selecione com V (ou a tecla \"anterior\") e " + how + " (padrão R).", lvl > 0 ? GREEN : 0xFFFF8855);
        }

        section("Origem anatômica", a.origin());
        section("Funcionamento", a.desc());
        section("Animação", a.anim());
        section("Efeitos visuais e sonoros", a.fx());
        section("Uso no jogo", a.gameplay());

        lines.add(new Line(Text.literal(" ").asOrderedText(), 0));
        lines.add(new Line(Text.literal("Evolução").asOrderedText(), 0xFF66CCFF));
        for (String e : a.evo()) wrap("• " + e, 0xFFDDDDDD);
        section("Mobs que fornecem", String.join(", ", names));
    }

    private void section(String title, String body) {
        if (body == null || body.isEmpty()) return;
        lines.add(new Line(Text.literal(" ").asOrderedText(), 0));
        lines.add(new Line(Text.literal(title).asOrderedText(), 0xFF66CCFF));
        wrap(body, 0xFFDDDDDD);
    }

    private void wrap(String s, int color) {
        int maxW = Math.max(40, detW - 12);
        for (OrderedText t : textRenderer.wrapLines(Text.literal(s), maxW)) lines.add(new Line(t, color));
    }

    // ------------------------------------------------------------------ render

    private float k() {
        return guiSize / (float) IMG_PX;
    }

    private float bandTop(int i) {
        return guiY + (BAND_Y0 + i * BAND_H) * k();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Habilidades funcionais (" + shown.size() + ")").formatted(Formatting.GRAY),
                width / 2, 19, 0xFFAAAAAA);

        // GUI (arte 91x91 ampliada em escala inteira, nítida)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.drawTexture(GUI_TEX, guiX, guiY, guiSize, guiSize, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);

        // lista dentro da parte escura
        float k = k();
        for (int i = 0; i < shown.size() && i < BAND_L.length; i++) {
            Ability a = shown.get(i);
            float x0 = guiX + BAND_L[i] * k, x1 = guiX + BAND_R[i] * k;
            float y0 = bandTop(i), h = BAND_H * k;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y0 + h;
            if (i == selected) ctx.fill((int) x0, (int) y0, (int) x1, (int) (y0 + h), 0x99AA2222);
            else if (hover) ctx.fill((int) x0, (int) y0, (int) x1, (int) (y0 + h), 0x44FFFFFF);

            boolean ok = available(a);
            String label = "\u25CF " + a.name();
            float natural = textRenderer.getWidth(label);
            float sc = Math.min(Math.min(0.85f, (h * 0.85f) / 8f), ((x1 - x0) - 4f) / natural);
            sc = Math.max(sc, 0.3f);
            float ty = y0 + (h - 8f * sc) / 2f + 0.5f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + 2f, ty, 0);
            ctx.getMatrices().scale(sc, sc, 1f);
            ctx.drawText(textRenderer, "\u25CF", 0, 0, ok ? GREEN : 0xFF777777, false);
            ctx.drawText(textRenderer, a.name(), textRenderer.getWidth("\u25CF "), 0,
                    i == selected ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            ctx.getMatrices().pop();
        }

        // detalhes
        ctx.fill(detX, detY, detX + detW, detY + detH, 0xAA000000);
        double maxDet = Math.max(0, lines.size() * LINE_H - (detH - 8));
        detailScroll = MathHelper.clamp(detailScroll, 0, maxDet);
        ctx.enableScissor(detX, detY, detX + detW, detY + detH);
        int y = detY + 4 - (int) detailScroll;
        for (Line l : lines) {
            if (y + LINE_H >= detY && y <= detY + detH) ctx.drawText(textRenderer, l.text(), detX + 6, y, l.color(), false);
            y += LINE_H;
        }
        ctx.disableScissor();
        if (maxDet > 0) { // barra de rolagem
            int barH = Math.max(12, (int) (detH * (detH / (double) (lines.size() * LINE_H))));
            int barY = detY + (int) ((detH - barH) * (detailScroll / maxDet));
            ctx.fill(detX + detW - 3, barY, detX + detW - 1, barY + barH, 0xAAFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        float k = k();
        for (int i = 0; i < shown.size() && i < BAND_L.length; i++) {
            float x0 = guiX + BAND_L[i] * k, x1 = guiX + BAND_R[i] * k;
            float y0 = bandTop(i);
            if (mx >= x0 && mx < x1 && my >= y0 && my < y0 + BAND_H * k) {
                select(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        detailScroll -= amount * LINE_H * 3;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            select(Math.min(shown.size() - 1, selected + 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            select(Math.max(0, selected - 1));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(int idx) {
        if (idx < 0 || idx >= shown.size()) return;
        selected = idx;
        rebuildLines();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
