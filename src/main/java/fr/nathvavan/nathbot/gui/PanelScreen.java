package fr.nathvavan.nathbot.gui;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.client.Keys;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.task.*;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.web.WebServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Panneau principal du bot (touche P par defaut). */
public class PanelScreen extends Screen {
    private EditBox cmd;

    public PanelScreen() {
        super(Component.literal("NathBot"));
    }

    private record Act(String label, String tip, Runnable r) {}

    @Override
    protected void init() {
        List<Act> acts = List.of(
                new Act("Chercher des diamants", "Le bot trouve et mine 5 diamants (il fabrique les pioches s'il faut)",
                        () -> Bot.I.start(new GatherTask(Items.DIAMOND, Inv.count(Items.DIAMOND) + 5))),
                new Act("Finir le jeu", "Du bois jusqu'au dragon de l'End, tout seul (experimental)", () -> Bot.I.start(new SpeedrunTask())),
                new Act("§cArreter", "Arrete la tache en cours", () -> Bot.I.stop()),
                new Act(Bot.I.paused ? "Reprendre" : "Pause", "Met en pause / reprend", () -> Bot.I.paused = !Bot.I.paused),
                new Act("Viens ici", "Va vers le joueur le plus proche", () -> Bot.I.start(new FollowTask(null, true))),
                new Act("Suivre", "Suit le joueur le plus proche", () -> Bot.I.start(new FollowTask(null, false))),
                new Act("Ferme auto", "Recolte et replante autour", () -> Bot.I.start(new MiscTasks.Farm(24))),
                new Act("Explorer", "Part droit devant en evitant les obstacles", () -> Bot.I.start(new MiscTasks.Explore(null))),
                new Act("Ramasser", "Ramasse les objets au sol", () -> Bot.I.start(new CollectDropsTask(24, 1200))),
                new Act("Surface", "Remonte a l'air libre", () -> Bot.I.start(new MiscTasks.Surface())),
                new Act("Miner du fer", "Mine 16 minerais de fer", () -> Commands.execute("miner fer 16")),
                new Act("Bois", "Coupe 16 buches", () -> Commands.execute("miner bois 16")),
                new Act("Abri rapide", "Construit un abri de survie devant toi", () -> Commands.execute("abri")),
                new Act("Constructions...", "Choisir une construction", () -> minecraft.setScreen(new BuildScreen(this))),
                new Act("Fonctions...", "Activer / couper les ~45 fonctions", () -> minecraft.setScreen(new FeaturesScreen(this))),
                new Act("Reglages...", "Options du bot et du site web", () -> minecraft.setScreen(new SettingsScreen(this))),
                new Act("§bOuvrir le site web", WebServer.url(), WebServer::openInBrowser),
                new Act("Aide", "Liste des commandes dans le chat", () -> Commands.execute("aide"))
        );
        int cols = 3, bw = 118, bh = 20, gap = 4;
        int totalW = cols * bw + (cols - 1) * gap;
        int x0 = (width - totalW) / 2;
        int y0 = 58;
        for (int i = 0; i < acts.size(); i++) {
            Act a = acts.get(i);
            int x = x0 + (i % cols) * (bw + gap);
            int y = y0 + (i / cols) * (bh + gap);
            addRenderableWidget(Button.builder(Component.literal(a.label()), b -> {
                a.r().run();
                if (!(minecraft.screen != this)) onClose();
            }).bounds(x, y, bw, bh).tooltip(Tooltip.create(Component.literal(a.tip()))).build());
        }
        int yCmd = y0 + ((acts.size() + cols - 1) / cols) * (bh + gap) + 8;
        cmd = new EditBox(font, x0, yCmd, totalW - 64, 20, Component.literal("commande"));
        cmd.setMaxLength(256);
        cmd.setHint(Component.literal("commande (ex: construire chateau, goto 100 64 -200)"));
        addRenderableWidget(cmd);
        addRenderableWidget(Button.builder(Component.literal("Executer"), b -> runCmd()).bounds(x0 + totalW - 60, yCmd, 60, 20).build());
    }

    private void runCmd() {
        String c = cmd.getValue().trim();
        if (c.isEmpty()) return;
        Commands.execute(c);
        cmd.setValue("");
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && cmd.isFocused()) {
            runCmd();
            return true;
        }
        if (!cmd.isFocused() && Keys.PANEL.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "§lNathBot§r §7- le bot de Nath et Vavan", width / 2, 12, 0x55FFFF);
        g.drawCenteredString(font, "§7Etat : §f" + Bot.I.statusText(), width / 2, 28, 0xFFFFFF);
        String web = WebServer.running() ? "§aSite web : " + WebServer.url() : "§cSite web arrete (voir Reglages)";
        g.drawCenteredString(font, web, width / 2, 40, 0xFFFFFF);
        super.render(g, mx, my, pt);
        List<String> last = BotLog.lines();
        int y = height - 12;
        for (int i = last.size() - 1; i >= 0 && i >= last.size() - 4; i--) {
            g.drawString(font, "§7" + last.get(i), 6, y, 0xAAAAAA);
            y -= 10;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
