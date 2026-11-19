package fr.nathvavan.nathbot.feature;

import fr.nathvavan.nathbot.client.Render;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Une fonction activable / desactivable (ESP, manger auto, HUD...). */
public abstract class Feature {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final boolean defaultOn;
    boolean enabled;
    protected final Minecraft mc = Minecraft.getInstance();

    protected Feature(String id, String name, String category, boolean defaultOn, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.defaultOn = defaultOn;
        this.description = description;
        this.enabled = defaultOn;
    }

    public boolean enabled() { return enabled; }

    /** Chaque tick client, si activee et en jeu. */
    public void tick() {}

    /** Dessin 3D, si activee. */
    public void render(Render.Ctx ctx) {}

    /** Lignes du HUD, si activee. */
    public void hud(List<String> lines) {}

    public void onEnable() {}

    public void onDisable() {}
}
