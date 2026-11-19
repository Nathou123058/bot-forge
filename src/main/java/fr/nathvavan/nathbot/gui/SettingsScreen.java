package fr.nathvavan.nathbot.gui;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.web.WebServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Reglages du bot (accessible aussi depuis Mods > NathBot > Config). */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private EditBox port;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Reglages NathBot"));
        this.parent = parent;
    }

    private Button toggle(int x, int y, String label, String tip, ForgeConfigSpec.BooleanValue v) {
        return Button.builder(lbl(label, v), b -> {
            v.set(!v.get());
            BotConfig.SPEC.save();
            b.setMessage(lbl(label, v));
        }).bounds(x, y, 170, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
    }

    private static Component lbl(String label, ForgeConfigSpec.BooleanValue v) {
        boolean on = BotConfig.get(v, false);
        return Component.literal(label + " : " + (on ? "§aOUI" : "§cNON"));
    }

    private Button number(int x, int y, String label, String tip, ForgeConfigSpec.IntValue v, int min, int max) {
        return Button.builder(Component.literal(label + " : " + BotConfig.get(v, min)), b -> {
            int n = BotConfig.get(v, min) + 1;
            if (n > max) n = min;
            v.set(n);
            BotConfig.SPEC.save();
            b.setMessage(Component.literal(label + " : " + n));
        }).bounds(x, y, 170, 20).tooltip(Tooltip.create(Component.literal(tip))).build();
    }

    @Override
    protected void init() {
        int x1 = width / 2 - 174, x2 = width / 2 + 4;
        int y = 36;
        addRenderableWidget(toggle(x1, y, "Casser des blocs", "Le bot peut casser pour passer", BotConfig.ALLOW_BREAK));
        addRenderableWidget(toggle(x2, y, "Poser des blocs", "Ponts et piliers", BotConfig.ALLOW_PLACE));
        y += 24;
        addRenderableWidget(toggle(x1, y, "Sprint", "Le bot court en ligne droite", BotConfig.ALLOW_SPRINT));
        addRenderableWidget(toggle(x2, y, "Creuser vers le bas", "Creuser tout droit sous soi (verifie la lave)", BotConfig.ALLOW_DIG_DOWN));
        y += 24;
        addRenderableWidget(toggle(x1, y, "Minage legit", "Ne cible que les minerais visibles (serveurs anti-xray)", BotConfig.LEGIT_MINING));
        addRenderableWidget(toggle(x2, y, "Rotation douce", "Camera qui tourne progressivement", BotConfig.SMOOTH_ROTATION));
        y += 24;
        addRenderableWidget(toggle(x1, y, "Construire en commandes", "Utilise /setblock si tu as les cheats (instantane)", BotConfig.BUILD_WITH_COMMANDS));
        addRenderableWidget(toggle(x2, y, "Chercher materiaux", "Si une construction manque de blocs, le bot va les recolter", BotConfig.AUTO_GATHER_MATERIALS));
        y += 24;
        addRenderableWidget(number(x1, y, "Chute max", "Hauteur de chute autorisee sans eau", BotConfig.MAX_FALL, 1, 10));
        addRenderableWidget(number(x2, y, "Manger sous", "Niveau de faim qui declenche le repas", BotConfig.EAT_BELOW_FOOD, 6, 19));
        y += 24;
        addRenderableWidget(number(x1, y, "Pause si vie <=", "Securite : pause du bot si la vie est basse (0 = jamais)", BotConfig.PANIC_HEALTH, 0, 12));
        addRenderableWidget(number(x2, y, "Rayon de recherche", "En chunks", BotConfig.SCAN_RADIUS_CHUNKS, 2, 12));
        y += 30;
        addRenderableWidget(toggle(x1, y, "Site web local", "Active le panneau web http://localhost", BotConfig.WEB_ENABLED));
        port = new EditBox(font, x2, y, 80, 20, Component.literal("port"));
        port.setValue(String.valueOf(BotConfig.get(BotConfig.WEB_PORT, 8765)));
        port.setFilter(s -> s.matches("\\d{0,5}"));
        addRenderableWidget(port);
        addRenderableWidget(Button.builder(Component.literal("Appliquer"), b -> {
            try {
                int p = Integer.parseInt(port.getValue());
                if (p >= 1024 && p <= 65535) {
                    BotConfig.WEB_PORT.set(p);
                    BotConfig.SPEC.save();
                }
            } catch (NumberFormatException ignored) {}
            WebServer.restart();
        }).bounds(x2 + 86, y, 84, 20).build());
        y += 24;
        addRenderableWidget(toggle(x1, y, "Navigateur avec la touche", "Ouvre aussi le site quand tu appuies sur la touche du panneau", BotConfig.WEB_OPEN_FROM_PANEL));
        addRenderableWidget(Button.builder(Component.literal("Ouvrir le site web"), b -> WebServer.openInBrowser()).bounds(x2, y, 170, 20).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal("Changer les touches (P, K, J, C...)"), b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(width / 2 - 120, y, 240, 20)
                .tooltip(Tooltip.create(Component.literal("Ouvre Options > Commandes : categorie NathBot"))).build());
        addRenderableWidget(Button.builder(Component.literal("Termine"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Reglages NathBot", width / 2, 14, 0x55FFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
