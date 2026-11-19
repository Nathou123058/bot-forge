package fr.nathvavan.nathbot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Touches du mod : modifiables dans Options > Commandes > NathBot. */
public final class Keys {
    private Keys() {}

    public static final String CAT = "key.categories.nathbot";

    public static final KeyMapping PANEL = new KeyMapping("key.nathbot.panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CAT);
    public static final KeyMapping STOP = new KeyMapping("key.nathbot.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CAT);
    public static final KeyMapping PAUSE = new KeyMapping("key.nathbot.pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CAT);
    public static final KeyMapping WEB = new KeyMapping("key.nathbot.web", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);
    public static final KeyMapping ZOOM = new KeyMapping("key.nathbot.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CAT);
    public static final KeyMapping DIAMONDS = new KeyMapping("key.nathbot.diamonds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);
    public static final KeyMapping FINISH = new KeyMapping("key.nathbot.finish", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);
    public static final KeyMapping ORE_ESP = new KeyMapping("key.nathbot.ore_esp", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);
    public static final KeyMapping FULLBRIGHT = new KeyMapping("key.nathbot.fullbright", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);
    public static final KeyMapping COME = new KeyMapping("key.nathbot.come", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CAT);

    public static final KeyMapping[] ALL = {PANEL, STOP, PAUSE, WEB, ZOOM, DIAMONDS, FINISH, ORE_ESP, FULLBRIGHT, COME};
}
