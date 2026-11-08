package com.devourer.client.gui;

import com.devourer.DevourPart;
import com.devourer.PlayerDevour;
import com.devourer.client.ClientData;
import com.devourer.client.gui.AbilityData.Ability;
import com.devourer.client.gui.AbilityData.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
 * Tela com todas as habilidades mutantes: lista à esquerda (por categoria) e detalhes à direita.
 * Roda do mouse rola a lista ou o texto; setas cima/baixo trocam a habilidade selecionada.
 */
public class AbilitiesScreen extends Screen {
    private static final int ENTRY_H = 14;
    private static final int LINE_H = 10;
    private static final int GREEN = 0xFF55FF55;

    private record Line(OrderedText text, int color) {
    }

    private final AbilityData data = AbilityData.load();
    private final Set<String> owned = new HashSet<>();

    private int catIndex = 0; // 0 = Todas
    private List<Ability> shown = new ArrayList<>();
    private int selected = 0;
    private double listScroll = 0, detailScroll = 0;
    private final List<Line> lines = new ArrayList<>();

    private int listX, listY, listW, listH, detX, detY, detW, detH;

    public AbilitiesScreen() {
        super(Text.literal("Habilidades mutantes"));
    }

    @Override
    protected void init() {
        owned.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            PlayerDevour d = ClientData.DATA.get(mc.player.getUuid());
            if (d != null) for (DevourPart p : d.parts) owned.add(p.species());
        }

        // abas de categoria (quebram em mais linhas se faltar largura)
        int bw = 74, bh = 18, gap = 2;
        int perRow = Math.max(1, (width - 20) / (bw + gap));
        int total = data.categories.size() + 1;
        int rows = (total + perRow - 1) / perRow;
        for (int i = 0; i < total; i++) {
            final int idx = i;
            int row = i / perRow, col = i % perRow;
            String name = i == 0 ? "Todas" : data.categories.get(i - 1).name();
            Text label = i == catIndex ? Text.literal(name).formatted(Formatting.YELLOW) : Text.literal(name);
            addDrawableChild(ButtonWidget.builder(label, b -> {
                catIndex = idx;
                selected = 0;
                listScroll = 0;
                detailScroll = 0;
                clearAndInit();
            }).dimensions(10 + col * (bw + gap), 34 + row * (bh + gap), bw, bh).build());
        }

        int top = 34 + rows * (bh + gap) + 6;
        listX = 10;
        listY = top;
        listW = MathHelper.clamp(width / 3, 120, 190);
        listH = height - top - 10;
        detX = listX + listW + 8;
        detY = top;
        detW = width - detX - 10;
        detH = listH;

        rebuildShown();
        rebuildLines();
    }

    private void rebuildShown() {
        shown = new ArrayList<>();
        String cat = catIndex == 0 ? null : data.categories.get(catIndex - 1).id();
        for (Ability a : data.abilities) if (cat == null || a.cat().equals(cat)) shown.add(a);
        selected = MathHelper.clamp(selected, 0, Math.max(0, shown.size() - 1));
    }

    // ------------------------------------------------------------------ disponibilidade

    /** Combinadas exigem todos os mobs; as demais, qualquer um. */
    private boolean available(Ability a) {
        if (a.mobs().isEmpty()) return false;
        boolean all = a.cat().equals("combinadas");
        int have = 0;
        for (String m : a.mobs()) if (owned.contains(m)) have++;
        return all ? have == a.mobs().size() : have > 0;
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
        String all = a.cat().equals("combinadas") ? " (precisa de todos)" : " (qualquer um)";
        if (available(a)) wrap("Você tem os membros necessários no corpo.", GREEN);
        else wrap("Ainda não disponível: devore " + String.join(", ", names) + all + ".", 0xFFFF8855);

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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Catálogo de habilidades — ainda em desenvolvimento (não funcionais em jogo)").formatted(Formatting.GRAY),
                width / 2, 20, 0xFFAAAAAA);

        // lista
        ctx.fill(listX, listY, listX + listW, listY + listH, 0xAA000000);
        double maxList = Math.max(0, shown.size() * ENTRY_H - listH);
        listScroll = MathHelper.clamp(listScroll, 0, maxList);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < shown.size(); i++) {
            int y = listY + i * ENTRY_H - (int) listScroll;
            if (y + ENTRY_H < listY || y > listY + listH) continue;
            Ability a = shown.get(i);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ENTRY_H;
            if (i == selected) ctx.fill(listX, y, listX + listW, y + ENTRY_H, 0x88AA2222);
            else if (hover) ctx.fill(listX, y, listX + listW, y + ENTRY_H, 0x44FFFFFF);
            boolean ok = available(a);
            ctx.drawText(textRenderer, ok ? "●" : "○", listX + 4, y + 3, ok ? GREEN : 0xFF777777, false);
            String name = textRenderer.trimToWidth(a.name(), listW - 20);
            ctx.drawText(textRenderer, name, listX + 14, y + 3, i == selected ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
        ctx.disableScissor();

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
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + listScroll) / ENTRY_H);
            if (idx >= 0 && idx < shown.size()) select(idx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx < detX) listScroll -= amount * ENTRY_H * 2;
        else detailScroll -= amount * LINE_H * 3;
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
        // mantém o item selecionado visível na lista
        int top = idx * ENTRY_H;
        if (top < listScroll) listScroll = top;
        else if (top + ENTRY_H > listScroll + listH) listScroll = top + ENTRY_H - listH;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
