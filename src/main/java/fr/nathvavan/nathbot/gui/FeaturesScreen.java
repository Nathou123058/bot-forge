package fr.nathvavan.nathbot.gui;

import fr.nathvavan.nathbot.feature.Feature;
import fr.nathvavan.nathbot.feature.Features;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Liste des fonctions activables, par pages. */
public class FeaturesScreen extends Screen {
    private final Screen parent;
    private int page = 0;

    public FeaturesScreen(Screen parent) {
        super(Component.literal("Fonctions NathBot"));
        this.parent = parent;
    }

    private int perPage() {
        return Math.max(6, ((height - 90) / 24) * 2);
    }

    @Override
    protected void init() {
        clearWidgets();
        List<Feature> all = new ArrayList<>(Features.all());
        int pp = perPage();
        int pages = (all.size() + pp - 1) / pp;
        page = Math.max(0, Math.min(page, pages - 1));
        int bw = 170, gap = 6;
        int x0 = width / 2 - bw - gap / 2;
        int y0 = 40;
        for (int i = 0; i < pp && page * pp + i < all.size(); i++) {
            Feature f = all.get(page * pp + i);
            int x = x0 + (i % 2) * (bw + gap);
            int y = y0 + (i / 2) * 24;
            addRenderableWidget(Button.builder(label(f), b -> {
                Features.toggle(f.id);
                b.setMessage(label(f));
            }).bounds(x, y, bw, 20).tooltip(Tooltip.create(Component.literal(f.category + " - " + f.description + " (id: " + f.id + ")"))).build());
        }
        int yb = height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; init(); }).bounds(width / 2 - 150, yb, 40, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; init(); }).bounds(width / 2 + 110, yb, 40, 20).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> minecraft.setScreen(parent)).bounds(width / 2 - 50, yb, 100, 20).build());
    }

    private static Component label(Feature f) {
        return Component.literal((f.enabled() ? "§a● " : "§c○ ") + "§f" + f.name);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Fonctions (" + Features.all().size() + ") - page " + (page + 1), width / 2, 16, 0x55FFFF);
        g.drawCenteredString(font, "§7Survole un bouton pour la description", width / 2, 27, 0xAAAAAA);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
