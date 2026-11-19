package fr.nathvavan.nathbot.command;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.Waypoints;
import fr.nathvavan.nathbot.build.Schematic;
import fr.nathvavan.nathbot.build.SchematicIO;
import fr.nathvavan.nathbot.build.Schematics;
import fr.nathvavan.nathbot.feature.Feature;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.task.*;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Targets;
import fr.nathvavan.nathbot.web.WebServer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.*;

/**
 * Toutes les commandes du bot. Utilisables avec /nb ..., avec #... dans le chat,
 * depuis le panneau (touche P) et depuis le site web.
 */
public final class Commands {
    private Commands() {}

    public record Help(String usage, String desc, String category) {}

    public static final List<Help> HELP = List.of(
            new Help("aide", "Liste des commandes", "General"),
            new Help("stop", "Arreter le bot", "General"),
            new Help("pause / reprendre", "Mettre en pause / reprendre la tache", "General"),
            new Help("etat", "Ce que fait le bot", "General"),
            new Help("web", "Ouvrir le panneau web dans le navigateur", "General"),
            new Help("goto <x> <y> <z>", "Aller a des coordonnees", "Deplacement"),
            new Help("goto <x> <z>", "Aller en X/Z (n'importe quelle hauteur)", "Deplacement"),
            new Help("goto <point>", "Aller a un point enregistre", "Deplacement"),
            new Help("goto y <Y>", "Monter / descendre a une couche", "Deplacement"),
            new Help("suivre [joueur]", "Suivre un joueur", "Deplacement"),
            new Help("viens [joueur]", "Aller jusqu'a un joueur", "Deplacement"),
            new Help("explorer [nord|sud|est|ouest]", "Partir explorer", "Deplacement"),
            new Help("surface", "Remonter a la surface", "Deplacement"),
            new Help("tunnel [longueur]", "Creuser un tunnel droit devant", "Deplacement"),
            new Help("diamants [n]", "Aller chercher des diamants tout seul", "Minage"),
            new Help("miner <bloc> [n]", "Miner un bloc (fer, or, bois, obsidian...)", "Minage"),
            new Help("recolter <objet> [n]", "Obtenir un objet (mine/fabrique/cuit)", "Minage"),
            new Help("fabriquer <objet> [n]", "Fabriquer tout seul (va chercher les ingredients, recettes des mods comprises)", "Fabrication"),
            new Help("recette <objet> [n]", "Expliquer comment le bot obtiendrait un objet", "Fabrication"),
            new Help("cuire <objet> [n]", "Cuire au four (ex: cuire iron_ingot 10)", "Fabrication"),
            new Help("construire <nom> [rotation 0-3]", "Construire devant toi", "Construction"),
            new Help("constructions", "Liste des constructions", "Construction"),
            new Help("materiaux <nom>", "Materiaux necessaires", "Construction"),
            new Help("abri", "Construire un abri de survie", "Construction"),
            new Help("portail", "Construire et allumer un portail du Nether", "Construction"),
            new Help("pos1 / pos2", "Selectionner une zone", "Construction"),
            new Help("sauver <nom>", "Sauvegarder la zone en construction (.nbt)", "Construction"),
            new Help("recharger", "Recharger les constructions du dossier", "Construction"),
            new Help("finir", "Finir le jeu tout seul (jusqu'au dragon)", "Aventure"),
            new Help("ferme [rayon]", "Recolter et replanter les cultures", "Ferme"),
            new Help("chasser <creature> [n]", "Chasser (cow, zombie, blaze...)", "Combat"),
            new Help("nettoyer [rayon]", "Tuer les monstres autour", "Combat"),
            new Help("ramasser", "Ramasser les objets au sol", "Divers"),
            new Help("manger", "Manger maintenant", "Divers"),
            new Help("dormir", "Aller dormir dans un lit", "Divers"),
            new Help("trier", "Trier l'inventaire", "Divers"),
            new Help("ranger", "Le bot ouvre l'inventaire et range tout (barre rapide, piles, dechets)", "Divers"),
            new Help("bateau <x> <z>", "Aller quelque part en bateau (il en fabrique un si besoin)", "Deplacement"),
            new Help("jeter <objet>", "Jeter tous les exemplaires d'un objet", "Divers"),
            new Help("wp ajouter|suppr|liste|suivre <nom>", "Points de passage", "Points"),
            new Help("sethome / home", "Definir / rejoindre la maison", "Points"),
            new Help("fonctions", "Liste des fonctions activables", "Reglages"),
            new Help("activer <fonction> / desactiver <fonction>", "Activer ou couper une fonction", "Reglages"),
            new Help("regler <option> <valeur>", "Changer un reglage (casser, poser, chute, sprint, legit, commandes)", "Reglages"),
            new Help("stats", "Statistiques de la session", "Divers"),
            new Help("scripts [recharger]", "Scripts JavaScript du dossier nathbot/scripts (recharges en direct)", "Developpement"),
            new Help("dev [forcer]", "Etat du mode developpement / debloquer le bot en urgence", "Developpement")
    );

    private static final Map<String, String> ITEM_ALIASES = new HashMap<>();

    static {
        String[][] a = {{"diamant", "diamond"}, {"diamants", "diamond"}, {"fer", "iron_ingot"}, {"lingot_fer", "iron_ingot"}, {"or", "gold_ingot"},
                {"charbon", "coal"}, {"bois", "oak_log"}, {"buche", "oak_log"}, {"planche", "oak_planks"}, {"planches", "oak_planks"},
                {"baton", "stick"}, {"batons", "stick"}, {"pierre", "cobblestone"}, {"etabli", "crafting_table"}, {"four", "furnace"},
                {"coffre", "chest"}, {"torche", "torch"}, {"torches", "torch"}, {"verre", "glass"}, {"sable", "sand"}, {"terre", "dirt"},
                {"obsidienne", "obsidian"}, {"seau", "bucket"}, {"briquet", "flint_and_steel"}, {"silex", "flint"}, {"pain", "bread"},
                {"pioche_bois", "wooden_pickaxe"}, {"pioche_pierre", "stone_pickaxe"}, {"pioche_fer", "iron_pickaxe"},
                {"pioche_diamant", "diamond_pickaxe"}, {"epee_fer", "iron_sword"}, {"epee_diamant", "diamond_sword"}, {"bouclier", "shield"},
                {"echelle", "ladder"}, {"lit", "white_bed"}, {"emeraude", "emerald"}, {"redstone", "redstone"}, {"lapis", "lapis_lazuli"},
                {"oeil", "ender_eye"}, {"yeux", "ender_eye"}, {"perle", "ender_pearl"}, {"poudre_blaze", "blaze_powder"}, {"fer_brut", "raw_iron"}};
        for (String[] x : a) ITEM_ALIASES.put(x[0], x[1]);
    }

    public static Item parseItem(String s) {
        String n = s.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        n = ITEM_ALIASES.getOrDefault(n, n);
        ResourceLocation rl = ResourceLocation.tryParse(n.contains(":") ? n : "minecraft:" + n);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) return BuiltInRegistries.ITEM.get(rl);
        return byName(s);
    }

    private static Map<String, Item> namesCache;
    private static String namesLang;

    private static String norm(String s) {
        String x = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return x.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /** Retrouve un objet par son nom affiche en jeu ("planches de bambou", "bambou"...), mods compris. */
    private static Item byName(String raw) {
        String lang = Minecraft.getInstance().options.languageCode;
        if (namesCache == null || !lang.equals(namesLang)) {
            Map<String, Item> m = new HashMap<>();
            for (Item i : BuiltInRegistries.ITEM) {
                try {
                    m.putIfAbsent(norm(i.getDescription().getString()), i);
                    m.putIfAbsent(norm(BuiltInRegistries.ITEM.getKey(i).getPath()), i);
                } catch (Throwable ignored) {}
            }
            namesCache = m;
            namesLang = lang;
        }
        String k = norm(raw);
        Item exact = namesCache.get(k);
        if (exact != null) return exact;
        // sinon : le nom le plus court qui contient le mot (ex : "bambou" -> Bambou)
        Item best = null;
        int bl = Integer.MAX_VALUE;
        for (Map.Entry<String, Item> e : namesCache.entrySet()) {
            if (e.getKey().contains(k) && e.getKey().length() < bl) { bl = e.getKey().length(); best = e.getValue(); }
        }
        return best;
    }

    private static int intArg(String[] a, int i, int def) {
        if (a.length <= i) return def;
        try {
            return Integer.parseInt(a[i]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int coord(String s, int base) {
        if (s.startsWith("~")) return base + (s.length() > 1 ? Integer.parseInt(s.substring(1)) : 0);
        return (int) Math.floor(Double.parseDouble(s));
    }

    private static Direction parseDir(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "nord", "north", "n" -> Direction.NORTH;
            case "sud", "south", "s" -> Direction.SOUTH;
            case "est", "east", "e" -> Direction.EAST;
            case "ouest", "west", "o", "w" -> Direction.WEST;
            default -> null;
        };
    }

    /** Point d'origine "devant le joueur" pour construire. */
    public static BlockPos buildOrigin(int dist) {
        Minecraft mc = Minecraft.getInstance();
        Direction d = mc.player.getDirection();
        return Bot.feet().relative(d, dist);
    }

    public static int rotationForFacing() {
        return switch (Minecraft.getInstance().player.getDirection()) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            default -> 3;
        };
    }

    public static void startBuild(String id, int rot, BlockPos origin) {
        Schematic s = Schematics.get(id);
        if (s == null) {
            BotLog.error("Construction inconnue : " + id + " (tape 'constructions')");
            return;
        }
        Schematic r = s.rotated(rot);
        BlockPos o = origin;
        if (o == null) {
            // on place la construction devant le joueur, decalee pour qu'elle ne l'englobe pas
            int[] size = r.size();
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos p : r.blocks.keySet()) {
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
            Direction d = Minecraft.getInstance().player.getDirection();
            BlockPos front = Bot.feet().relative(d, 2);
            int ox = front.getX(), oz = front.getZ();
            switch (d) {
                case SOUTH -> { ox -= (minX + maxX) / 2; oz -= minZ; }
                case NORTH -> { ox -= (minX + maxX) / 2; oz -= maxZ; }
                case EAST -> { ox -= minX; oz -= (minZ + maxZ) / 2; }
                default -> { ox -= maxX; oz -= (minZ + maxZ) / 2; }
            }
            o = new BlockPos(ox, Bot.feet().getY(), oz);
            BotLog.info("Taille : " + size[0] + "x" + size[1] + "x" + size[2]);
        }
        boolean cmds = BotConfig.get(BotConfig.BUILD_WITH_COMMANDS, false) && Minecraft.getInstance().player.hasPermissions(2);
        Bot.I.start(new BuildTask(r, o, cmds));
    }

    public static void execute(String raw) {
        String line = raw.trim();
        if (line.startsWith("/")) line = line.substring(1);
        if (line.toLowerCase(Locale.ROOT).startsWith("nb ")) line = line.substring(3).trim();
        if (line.isEmpty()) {
            help();
            return;
        }
        String[] a = line.split("\\s+");
        String c = a[0].toLowerCase(Locale.ROOT);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos feet = Bot.feet();
        try {
            switch (c) {
                case "aide", "help", "?" -> help();
                case "stop", "arret", "arreter", "cancel" -> Bot.I.stop();
                case "pause" -> { Bot.I.paused = true; BotLog.info("Pause."); }
                case "reprendre", "resume", "continuer" -> {
                    if (fr.nathvavan.nathbot.dev.DevMode.locked()) { fr.nathvavan.nathbot.dev.DevMode.refuse(); return; }
                    Bot.I.paused = false;
                    BotLog.info("Reprise.");
                }
                case "scripts" -> {
                    if (a.length > 1 && a[1].startsWith("rech")) BotLog.ok(fr.nathvavan.nathbot.dev.Scripts.reloadAll() + " script(s) recharge(s).");
                    var l = fr.nathvavan.nathbot.dev.Scripts.info();
                    if (l.isEmpty()) BotLog.info("Aucun script. Dossier : " + fr.nathvavan.nathbot.dev.Scripts.dir().getPath());
                    for (var s : l) {
                        if (s.ok()) BotLog.ok(s.file() + " : ok (" + s.commands() + " commande(s), " + s.handlers() + " evenement(s))");
                        else BotLog.error(s.file() + " : " + s.error());
                    }
                    for (String[] sc : fr.nathvavan.nathbot.dev.Scripts.commands()) BotLog.info("/nb " + sc[0] + " : " + sc[1] + " (" + sc[2] + ")");
                }
                case "dev" -> {
                    if (a.length > 1 && a[1].equalsIgnoreCase("forcer")) {
                        fr.nathvavan.nathbot.dev.DevMode.unlock("(debloque a la main)");
                    } else {
                        BotLog.info(fr.nathvavan.nathbot.dev.DevMode.locked()
                                ? "Claude code le bot : " + fr.nathvavan.nathbot.dev.DevMode.reason() + " (encore " + fr.nathvavan.nathbot.dev.DevMode.secondsLeft() / 60 + " min max). En cas d'urgence : /nb dev forcer"
                                : "Mode developpement libre. Scripts : " + fr.nathvavan.nathbot.dev.Scripts.dir().getPath());
                    }
                }
                case "etat", "status" -> BotLog.info(Bot.I.statusText());
                case "web", "site" -> WebServer.openInBrowser();
                case "goto", "aller", "va" -> {
                    if (a.length == 4) {
                        Bot.I.start(new GotoTask(new Goal.Block(coord(a[1], feet.getX()), coord(a[2], feet.getY()), coord(a[3], feet.getZ()))));
                    } else if (a.length == 3 && a[1].equalsIgnoreCase("y")) {
                        Bot.I.start(new GotoTask(new Goal.Y(coord(a[2], feet.getY()))));
                    } else if (a.length == 3) {
                        Bot.I.start(new GotoTask(new Goal.XZ(coord(a[1], feet.getX()), coord(a[2], feet.getZ()))));
                    } else if (a.length == 2) {
                        Waypoints.Wp w = Waypoints.get(a[1]);
                        if (w == null) BotLog.error("Point inconnu : " + a[1]);
                        else Bot.I.start(new GotoTask(new Goal.Block(w.x, w.y, w.z)));
                    } else BotLog.error("Utilisation : goto x y z | goto x z | goto <point>");
                }
                case "suivre", "follow" -> Bot.I.start(new FollowTask(a.length > 1 ? a[1] : null, false));
                case "viens", "come" -> Bot.I.start(new FollowTask(a.length > 1 ? a[1] : null, true));
                case "explorer", "explore" -> Bot.I.start(new MiscTasks.Explore(a.length > 1 ? parseDir(a[1]) : null));
                case "surface" -> Bot.I.start(new MiscTasks.Surface());
                case "tunnel" -> Bot.I.start(new MiscTasks.Tunnel(intArg(a, 1, 32)));
                case "diamants", "diamonds", "diamant" -> {
                    int n = intArg(a, 1, 5);
                    Bot.I.start(new GatherTask(Items.DIAMOND, Inv.count(Items.DIAMOND) + n));
                }
                case "miner", "mine" -> {
                    if (a.length < 2) { BotLog.error("Utilisation : miner <bloc> [nombre]"); return; }
                    Targets.Spec spec = Targets.parse(a[1]);
                    if (spec.blocks().isEmpty()) { BotLog.error("Bloc inconnu : " + a[1]); return; }
                    Bot.I.start(new MineTask(spec, intArg(a, 2, 16)));
                }
                case "recolter", "get", "obtenir" -> {
                    Item it = a.length > 1 ? parseItem(a[1]) : null;
                    if (it == null) { BotLog.error("Objet inconnu."); return; }
                    Bot.I.start(new GatherTask(it, Inv.count(it) + intArg(a, 2, 1)));
                }
                case "fabriquer", "craft" -> {
                    Item it = a.length > 1 ? parseItem(a[1]) : null;
                    if (it == null) { BotLog.error("Objet inconnu."); return; }
                    Bot.I.start(new GatherTask(it, Inv.count(it) + intArg(a, 2, 1)));
                }
                case "recette", "recipe", "comment" -> {
                    Item it = a.length > 1 ? parseItem(a[1]) : null;
                    if (it == null) { BotLog.error("Objet inconnu."); return; }
                    int n = intArg(a, 2, 1);
                    BotLog.info("Pour obtenir " + n + "x " + it.getDescription().getString() + " :");
                    for (String l : new Planner().explain(it, n)) BotLog.info(l);
                }
                case "cuire", "smelt" -> {
                    Item it = a.length > 1 ? parseItem(a[1]) : null;
                    if (it == null) { BotLog.error("Objet inconnu."); return; }
                    SmeltingRecipe rec = null;
                    for (SmeltingRecipe r : mc.level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                        if (r.getResultItem(mc.level.registryAccess()).is(it) && !r.getIngredients().isEmpty() && Inv.count(r.getIngredients().get(0)) > 0) { rec = r; break; }
                    }
                    if (rec == null) { BotLog.error("Rien a cuire pour obtenir " + it.getDescription().getString()); return; }
                    var ing = rec.getIngredients().get(0);
                    Bot.I.start(new ContainerTasks.Smelt(ing, it, Inv.count(it) + intArg(a, 2, 1)));
                }
                case "construire", "build" -> {
                    if (a.length < 2) { listBuilds(); return; }
                    int rot = a.length > 2 ? intArg(a, 2, 0) : rotationForFacing();
                    BlockPos o = null;
                    if (a.length >= 6) o = new BlockPos(coord(a[3], feet.getX()), coord(a[4], feet.getY()), coord(a[5], feet.getZ()));
                    startBuild(a[1], rot, o);
                }
                case "constructions", "builds", "plans" -> listBuilds();
                case "materiaux", "materials" -> {
                    Schematic s = a.length > 1 ? Schematics.get(a[1]) : null;
                    if (s == null) { BotLog.error("Construction inconnue."); return; }
                    StringBuilder sb = new StringBuilder(s.name + " : ");
                    s.materials().forEach((i, n) -> {
                        int have = Inv.count(i);
                        sb.append(n).append("x ").append(i.getDescription().getString()).append(have >= n ? " (ok)" : " (manque " + (n - have) + ")").append(", ");
                    });
                    BotLog.info(sb.toString());
                }
                case "abri", "shelter" -> startBuild("abri_survie", rotationForFacing(), null);
                case "portail", "portal" -> {
                    if (Inv.count(Items.OBSIDIAN) < 10 && !mc.player.getAbilities().instabuild) {
                        BotLog.warn("Il faut 10 obsidiennes. Je lance 'Finir le jeu' qui sait les obtenir ? Tape 'recolter obsidian 10'.");
                        return;
                    }
                    BlockPos o = buildOrigin(3);
                    Bot.I.start(new MiscTasks.Sequence("Portail du Nether", List.of(
                            new BuildTask(Schematics.get("portail_nether"), o, false),
                            new SpeedrunTask.LightPortal(o))));
                }
                case "pos1" -> { SchematicIO.pos1 = feet; BotLog.info("Position 1 : " + feet.toShortString()); }
                case "pos2" -> { SchematicIO.pos2 = feet; BotLog.info("Position 2 : " + feet.toShortString()); }
                case "sauver", "save" -> BotLog.info(SchematicIO.saveSelection(a.length > 1 ? a[1] : "ma_construction"));
                case "recharger", "reload" -> BotLog.info(SchematicIO.reload() + " construction(s) chargee(s) depuis " + SchematicIO.dir().getPath());
                case "finir", "finish", "speedrun" -> Bot.I.start(new SpeedrunTask());
                case "ferme", "farm" -> Bot.I.start(new MiscTasks.Farm(intArg(a, 1, 24)));
                case "chasser", "kill", "tuer" -> {
                    if (a.length < 2) { BotLog.error("Utilisation : chasser <creature> [n]"); return; }
                    String id = a[1].toLowerCase(Locale.ROOT);
                    ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
                    EntityType<?> type = rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl) ? BuiltInRegistries.ENTITY_TYPE.get(rl) : null;
                    if (type == null) { BotLog.error("Creature inconnue : " + a[1]); return; }
                    final EntityType<?> t = type;
                    Bot.I.start(new KillTask(e -> e.getType() == t, intArg(a, 2, 5), type.getDescription().getString()));
                }
                case "nettoyer", "clear" -> {
                    int r = intArg(a, 1, 16);
                    Bot.I.start(new KillTask(e -> e instanceof Enemy, 0, "monstres", r).until(() ->
                            mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r), e -> e instanceof Enemy && e.isAlive()).isEmpty()));
                }
                case "ramasser", "collect", "pickup" -> Bot.I.start(new CollectDropsTask(24, 20 * 60));
                case "manger", "eat" -> {
                    Features.set("auto_eat", true);
                    BotLog.info("Je mange des que necessaire (fonction 'Manger automatiquement' activee).");
                }
                case "dormir", "sleep" -> Bot.I.start(new MiscTasks.Sleep());
                case "trier", "sort" -> sortInventory();
                case "ranger", "organiser", "organize" -> {
                    fr.nathvavan.nathbot.util.InvManager.requestOrganize();
                    BotLog.info("Je range l'inventaire...");
                }
                case "bateau", "boat" -> {
                    if (a.length < 3) { BotLog.error("Utilisation : bateau <x> <z>"); return; }
                    Bot.I.start(new BoatTask(coord(a[1], feet.getX()), coord(a[2], feet.getZ())));
                }
                case "slot" -> {
                    int i = intArg(a, 2, -1);
                    if (a.length < 3 || i < 0 || i > 35) { BotLog.error("Utilisation : slot jeter|main <0-35>"); return; }
                    var inv = mc.player.getInventory();
                    if (a[1].equalsIgnoreCase("jeter")) Inv.click(Inv.menuSlotFor(i), 1, ClickType.THROW);
                    else if (i < 9) inv.selected = i;
                    else Inv.click(Inv.menuSlotFor(i), inv.selected, ClickType.SWAP);
                }
                case "jeter", "drop" -> {
                    Item it = a.length > 1 ? parseItem(a[1]) : null;
                    if (it == null) { BotLog.error("Objet inconnu."); return; }
                    int n = 0;
                    for (int i = 0; i < 36; i++) if (Inv.get(i).is(it)) { Inv.click(Inv.menuSlotFor(i), 1, ClickType.THROW); n++; }
                    BotLog.info(n + " pile(s) jetee(s).");
                }
                case "wp", "point", "points" -> waypoint(a, feet);
                case "sethome" -> { Waypoints.set("maison", feet); BotLog.ok("Maison definie en " + feet.toShortString()); }
                case "home", "maison" -> {
                    Waypoints.Wp w = Waypoints.get("maison");
                    if (w == null) BotLog.error("Pas de maison. Tape 'sethome'.");
                    else Bot.I.start(new GotoTask(new Goal.Block(w.x, w.y, w.z)));
                }
                case "fonctions", "features" -> {
                    StringBuilder sb = new StringBuilder();
                    for (Feature f : Features.all()) sb.append(f.enabled() ? "[ON] " : "[--] ").append(f.id).append(", ");
                    BotLog.info(sb.toString());
                }
                case "activer", "on", "enable" -> toggle(a, true);
                case "desactiver", "off", "disable" -> toggle(a, false);
                case "toggle", "basculer" -> {
                    if (a.length > 1 && Features.toggle(a[1])) BotLog.info(a[1] + " : " + (Features.isOn(a[1]) ? "active" : "desactive"));
                    else BotLog.error("Fonction inconnue.");
                }
                case "regler", "set" -> setting(a);
                case "stats" -> {
                    var s = Bot.I.stats;
                    BotLog.info("Blocs mines " + s.blocksMined + ", distance " + (int) s.distance + "m, kills " + s.kills + ", morts " + s.deaths
                            + ", taches " + s.tasksDone + "/" + s.tasksStarted + ", temps " + s.seconds() / 60 + " min");
                }
                default -> {
                    boolean done;
                    try {
                        done = fr.nathvavan.nathbot.dev.Scripts.runCommand(c, Arrays.copyOfRange(a, 1, a.length));
                    } catch (LinkageError e) {
                        done = false;
                    }
                    if (!done) BotLog.error("Commande inconnue : " + c + " (tape 'aide')");
                }
            }
        } catch (Exception e) {
            BotLog.error("Erreur : " + e.getMessage());
        }
    }

    private static void help() {
        StringBuilder sb = new StringBuilder("Commandes (/nb ... ou #...) : ");
        for (Help h : HELP) sb.append(h.usage()).append(" | ");
        BotLog.info(sb.toString());
        BotLog.info("Touche P = panneau. Site web : http://localhost:" + BotConfig.get(BotConfig.WEB_PORT, 8765));
    }

    private static void listBuilds() {
        StringBuilder sb = new StringBuilder("Constructions : ");
        for (Schematic s : Schematics.all().values()) sb.append(s.id).append(", ");
        BotLog.info(sb.toString());
    }

    private static void toggle(String[] a, boolean on) {
        if (a.length < 2 || Features.get(a[1]) == null) {
            BotLog.error("Fonction inconnue (tape 'fonctions').");
            return;
        }
        Features.set(a[1], on);
        BotLog.info(Features.get(a[1]).name + " : " + (on ? "activee" : "desactivee"));
    }

    private static void waypoint(String[] a, BlockPos feet) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        switch (sub) {
            case "ajouter", "add", "set" -> {
                if (a.length < 3) { BotLog.error("wp ajouter <nom>"); return; }
                Waypoints.set(a[2], feet);
                BotLog.ok("Point '" + a[2] + "' enregistre en " + feet.toShortString());
            }
            case "suppr", "del", "supprimer" -> BotLog.info(a.length > 2 && Waypoints.remove(a[2]) ? "Supprime." : "Point inconnu.");
            case "suivre", "track" -> {
                Waypoints.active = a.length > 2 ? a[2] : null;
                BotLog.info(Waypoints.active == null ? "Boussole coupee." : "Boussole vers " + Waypoints.active);
            }
            case "goto", "aller" -> {
                Waypoints.Wp w = a.length > 2 ? Waypoints.get(a[2]) : null;
                if (w == null) BotLog.error("Point inconnu.");
                else Bot.I.start(new GotoTask(new Goal.Block(w.x, w.y, w.z)));
            }
            default -> {
                StringBuilder sb = new StringBuilder("Points : ");
                for (Waypoints.Wp w : Waypoints.all()) sb.append(w.name).append(" (").append(w.x).append(' ').append(w.y).append(' ').append(w.z).append(", ").append(w.dim.replace("minecraft:", "")).append(") ");
                BotLog.info(sb.toString());
            }
        }
    }

    private static void setting(String[] a) {
        if (a.length < 3) {
            BotLog.info("Options : casser, poser, sprint, creuser_bas, chute <n>, legit, commandes, recolte_auto, rotation_douce, port <n>, manger_sous <n>, panique <n>");
            return;
        }
        String k = a[1].toLowerCase(Locale.ROOT), v = a[2].toLowerCase(Locale.ROOT);
        boolean b = v.equals("on") || v.equals("oui") || v.equals("true") || v.equals("1");
        switch (k) {
            case "casser", "allowbreak" -> BotConfig.ALLOW_BREAK.set(b);
            case "poser", "allowplace" -> BotConfig.ALLOW_PLACE.set(b);
            case "sprint" -> BotConfig.ALLOW_SPRINT.set(b);
            case "creuser_bas", "digdown" -> BotConfig.ALLOW_DIG_DOWN.set(b);
            case "chute", "maxfall" -> BotConfig.MAX_FALL.set(Math.max(1, Math.min(20, Integer.parseInt(v))));
            case "legit" -> BotConfig.LEGIT_MINING.set(b);
            case "commandes", "commands" -> BotConfig.BUILD_WITH_COMMANDS.set(b);
            case "recolte_auto", "autogather" -> BotConfig.AUTO_GATHER_MATERIALS.set(b);
            case "rotation_douce", "smooth" -> BotConfig.SMOOTH_ROTATION.set(b);
            case "port" -> BotConfig.WEB_PORT.set(Integer.parseInt(v));
            case "manger_sous", "eat" -> BotConfig.EAT_BELOW_FOOD.set(Integer.parseInt(v));
            case "panique", "panic" -> BotConfig.PANIC_HEALTH.set(Integer.parseInt(v));
            default -> { BotLog.error("Option inconnue."); return; }
        }
        BotConfig.SPEC.save();
        BotLog.ok("Reglage " + k + " = " + v);
        if (k.equals("port")) WebServer.restart();
    }

    /** Trie l'inventaire principal (hors barre rapide) par type d'objet. */
    public static void sortInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            BotLog.warn("Ferme les interfaces avant de trier.");
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 9; i < 36; i++) items.add(Inv.get(i).copy());
        items.sort(Comparator.comparing((ItemStack s) -> s.isEmpty() ? "zzzz" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing(s -> -s.getCount()));
        for (int t = 0; t < 27; t++) {
            ItemStack want = items.get(t);
            int target = 9 + t;
            ItemStack cur = Inv.get(target);
            if (ItemStack.isSameItemSameTags(cur, want) && cur.getCount() == want.getCount()) continue;
            for (int s = target + 1; s < 36; s++) {
                ItemStack src = Inv.get(s);
                if (ItemStack.isSameItemSameTags(src, want) && src.getCount() == want.getCount()) {
                    Inv.click(Inv.menuSlotFor(s), 0, ClickType.PICKUP);
                    Inv.click(Inv.menuSlotFor(target), 0, ClickType.PICKUP);
                    Inv.click(Inv.menuSlotFor(s), 0, ClickType.PICKUP);
                    break;
                }
            }
        }
        BotLog.ok("Inventaire trie.");
    }
}
