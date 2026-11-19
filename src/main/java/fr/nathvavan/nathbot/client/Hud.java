package fr.nathvavan.nathbot.client;

import fr.nathvavan.nathbot.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** Affichage en haut a gauche de l'ecran. */
public final class Hud {
    private Hud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;
        List<String> lines = Features.hudLines();
        if (lines.isEmpty()) return;
        int y = 4;
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, mc.font.width(l));
        g.fill(2, 2, 6 + maxW + 2, 4 + lines.size() * 10 + 1, 0x70000000);
        for (String l : lines) {
            g.drawString(mc.font, l, 5, y, 0xFFFFFF, true);
            y += 10;
        }
    }
}
