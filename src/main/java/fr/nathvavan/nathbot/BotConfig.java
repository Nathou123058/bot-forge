package fr.nathvavan.nathbot;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuration client (fichier config/nathbot-client.toml, modifiable aussi en jeu). */
public final class BotConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue WEB_ENABLED;
    public static final ForgeConfigSpec.IntValue WEB_PORT;
    public static final ForgeConfigSpec.BooleanValue WEB_OPEN_FROM_PANEL;

    public static final ForgeConfigSpec.BooleanValue ALLOW_BREAK;
    public static final ForgeConfigSpec.BooleanValue ALLOW_PLACE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SPRINT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_DIG_DOWN;
    public static final ForgeConfigSpec.IntValue MAX_FALL;
    public static final ForgeConfigSpec.IntValue PATH_TIMEOUT_MS;
    public static final ForgeConfigSpec.BooleanValue SMOOTH_ROTATION;
    public static final ForgeConfigSpec.BooleanValue LEGIT_MINING;
    public static final ForgeConfigSpec.IntValue SCAN_RADIUS_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue BUILD_WITH_COMMANDS;
    public static final ForgeConfigSpec.BooleanValue AUTO_GATHER_MATERIALS;
    public static final ForgeConfigSpec.IntValue EAT_BELOW_FOOD;
    public static final ForgeConfigSpec.IntValue PANIC_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> THROWAWAY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Panneau web local").push("web");
        WEB_ENABLED = b.comment("Activer le site web local (http://localhost:<port>)").define("enabled", true);
        WEB_PORT = b.comment("Port du site web local").defineInRange("port", 8765, 1024, 65535);
        WEB_OPEN_FROM_PANEL = b.comment("Ouvrir automatiquement le navigateur quand on appuie sur la touche du panneau")
                .define("openBrowserWithKey", false);
        b.pop();

        b.comment("Comportement du bot").push("bot");
        ALLOW_BREAK = b.comment("Le bot peut casser des blocs pour passer").define("allowBreak", true);
        ALLOW_PLACE = b.comment("Le bot peut poser des blocs (ponts, piliers)").define("allowPlace", true);
        ALLOW_SPRINT = b.define("allowSprint", true);
        ALLOW_DIG_DOWN = b.comment("Autoriser a creuser tout droit vers le bas (verifie la lave)").define("allowDigDown", true);
        MAX_FALL = b.comment("Hauteur de chute max sans eau").defineInRange("maxFall", 3, 1, 20);
        PATH_TIMEOUT_MS = b.comment("Temps max de calcul d'un chemin (ms)").defineInRange("pathTimeoutMs", 2500, 200, 20000);
        SMOOTH_ROTATION = b.comment("Rotation de camera progressive (plus naturel)").define("smoothRotation", true);
        LEGIT_MINING = b.comment("Ne cibler que les minerais visibles (anti-xray)").define("legitMining", false);
        SCAN_RADIUS_CHUNKS = b.comment("Rayon de recherche des blocs, en chunks").defineInRange("scanRadiusChunks", 6, 1, 16);
        BUILD_WITH_COMMANDS = b.comment("Construire avec /setblock si on a les droits (creatif / cheats)").define("buildWithCommands", false);
        AUTO_GATHER_MATERIALS = b.comment("Aller chercher les materiaux manquants des constructions").define("autoGatherMaterials", true);
        EAT_BELOW_FOOD = b.comment("Manger quand la faim descend sous cette valeur").defineInRange("eatBelowFood", 14, 1, 19);
        PANIC_HEALTH = b.comment("Mettre le bot en pause si la vie descend sous cette valeur (0 = jamais)").defineInRange("panicHealth", 5, 0, 19);
        THROWAWAY = b.comment("Blocs que le bot peut poser pour se deplacer")
                .defineList("throwawayBlocks", List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt",
                        "minecraft:netherrack", "minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                        "minecraft:tuff", "minecraft:blackstone", "minecraft:deepslate", "minecraft:end_stone", "minecraft:basalt"),
                        o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private BotConfig() {}

    /** Lecture sure (la config peut ne pas etre encore chargee). */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> v, T def) {
        try {
            return v.get();
        } catch (Throwable t) {
            return def;
        }
    }
}
