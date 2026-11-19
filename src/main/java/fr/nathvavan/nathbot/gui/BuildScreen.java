package fr.nathvavan.nathbot.gui;

import fr.nathvavan.nathbot.build.Schematic;
import fr.nathvavan.nathbot.build.Schematics;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Choix d'une construction a faire construire par le bot. */
public class BuildScreen extends Screen {
    private final Screen parent;
    private int page = 0;
    private int rotation = -1;

    public BuildScreen(Screen parent) {
        super(Component.literal("Constructions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        List<Schematic> all = new ArrayList<>(Schematics.all().values());
        int pp = Math.max(6, ((height - 100) / 24) * 2);
        int pages = Math.max(1, (all.size() + pp - 1) / pp);
        page = Math.max(0, Math.min(page, pages - 1));
        int bw = 170, gap = 6;
        int x0 = width / 2 - bw - gap / 2;
        for (int i = 0; i < pp && page * pp + i < all.size(); i++) {
            Schematic s = all.get(page * pp + i);
            int x = x0 + (i % 2) * (bw + gap);
            int y = 46 + (i / 2) * 24;
            StringBuilder tip = new StringBuilder(s.description).append("\n\nMateriaux :");
            int shown = 0;
            for (Map.Entry<Item, Integer> e : s.materials().entrySet()) {
                if (shown++ > 10) { tip.append("\n..."); break; }
                int have = Inv.player() == null ? 0 : Inv.count(e.getKey());
                tip.append("\n").append(have >= e.getValue() ? "§a" : "§c").append(e.getValue()).append("x ").append(e.getKey().getDescription().getString())
                        .append(" §7(").append(have).append(")");
            }
            int[] sz = s.size();
            addRenderableWidget(Button.builder(Component.literal(s.name + " §7" + sz[0] + "x" + sz[1] + "x" + sz[2]), b -> {
                int rot = rotation < 0 ? Commands.rotationForFacing() : rotation;
                Commands.startBuild(s.id, rot, null);
                minecraft.setScreen(null);
            }).bounds(x, y, bw, 20).tooltip(Tooltip.create(Component.literal(tip.toString()))).build());
        }
        int yb = height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; init(); }).bounds(width / 2 - 200, yb, 30, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(rotLabel()), b -> {
            rotation = rotation >= 3 ? -1 : rotation + 1;
            b.setMessage(Component.literal(rotLabel()));
        }).bounds(width / 2 - 165, yb, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recharger"), b -> {
            Commands.execute("recharger");
            init();
        }).bounds(width / 2 - 50, yb, 80, 20).tooltip(Tooltip.create(Component.literal("Recharge les fichiers .nbt de .minecraft/nathbot/schematics"))).build());
        addRenderableWidget(Button.builder(Component.literal("Retour"), b -> minecraft.setScreen(parent)).bounds(width / 2 + 35, yb, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; init(); }).bounds(width / 2 + 170, yb, 30, 20).build()).active = page < pages - 1;
    }

    private String rotLabel() {
        return rotation < 0 ? "Rotation : auto" : "Rotation : " + rotation * 90 + "°";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Constructions (" + Schematics.all().size() + ") - page " + (page + 1), width / 2, 14, 0x55FFFF);
        g.drawCenteredString(font, "§7Le bot construit devant toi. Survole pour voir les materiaux.", width / 2, 26, 0xAAAAAA);
        g.drawCenteredString(font, "§7S'il manque des blocs, il va les chercher (bois, pierre, sable, verre...).", width / 2, 36, 0xAAAAAA);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
