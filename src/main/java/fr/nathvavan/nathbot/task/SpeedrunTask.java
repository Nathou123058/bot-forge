package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.Waypoints;
import fr.nathvavan.nathbot.build.Schematics;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.Targets;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * "Finir le jeu" : enchaine automatiquement toutes les etapes jusqu'au dragon.
 * bois -> pioches -> fer -> diamants -> obsidienne -> portail -> Nether (blazes, perles)
 * -> yeux de l'Ender -> fort -> portail de l'End -> dragon.
 * Chaque etape est recalculee a partir de l'inventaire : si le joueur meurt, le bot reprend.
 */
public class SpeedrunTask extends Task {
    private String stage = "demarrage";
    private BlockPos overworldPortal;
    private BlockPos netherArrival;
    private BlockPos strongholdEstimate;
    private int netherPearlRounds = 0;
    private int failures = 0;
    private BlockPos portalOrigin;
    private ResourceKey<Level> lastDim;
    // techniques "pro"
    private Item pendingTool, pendingCook, pendingArmor;
    private boolean toolInterrupt = false;
    private int blockTries = 0;
    private static long lastActiveTick = -1000;

    /** Vrai pendant que "Finir le jeu" tourne (active le sprint saute). */
    public static boolean isRunning() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() - lastActiveTick < 10;
    }

    private static void markActive() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) lastActiveTick = mc.level.getGameTime();
    }

    @Override
    public String name() { return "Finir le jeu [" + stage + "]"; }

    private static int rods() { return Inv.count(Items.BLAZE_ROD); }
    private static int powder() { return Inv.count(Items.BLAZE_POWDER); }
    private static int pearls() { return Inv.count(Items.ENDER_PEARL); }
    private static int eyes() { return Inv.count(Items.ENDER_EYE); }

    private static int food() {
        return Inv.count(Inv::isGoodFood);
    }

    private Status go(String st, Task t) {
        if (!st.equals(stage)) BotLog.info("Etape : " + st);
        stage = st;
        run(t);
        return Status.RUNNING;
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        markActive();
        // pioche presque cassee en plein minage : on s'arrete, on en refait une, on reprend
        if (ticks % 40 == 0 && pendingTool == null && mc.level.dimension() != Level.END
                && (child instanceof MineTask || child instanceof GatherTask || child instanceof Obsidian)) {
            if (ProTools.replacementNeeded() != null) {
                toolInterrupt = true;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        if (toolInterrupt) {
            toolInterrupt = false; // pas un echec : on a juste coupe pour refaire un outil
            return;
        }
        if (pendingTool != null) { if (result == Status.FAILED) ProTools.markFailed(pendingTool); pendingTool = null; }
        if (pendingCook != null) { if (result == Status.FAILED) ProTools.markFailed(pendingCook); pendingCook = null; }
        if (pendingArmor != null) { if (result == Status.FAILED) ProTools.markFailed(pendingArmor); pendingArmor = null; }
        if (result == Status.FAILED) {
            failures++;
            BotLog.warn("Etape '" + stage + "' ratee (" + failures + "), on retente.");
        } else failures = Math.max(0, failures - 1);
    }

    @Override
    protected Status update() {
        markActive();
        if (ticks == 1) {
            Features.set("auto_eat", true);
            Features.set("kill_aura", true);
            Features.set("auto_armor", true);
            Features.set("auto_respawn", true);
            BotLog.info("Mode 'Finir le jeu' lance. C'est long et experimental : surveille-le !");
        }
        if (mc.screen instanceof WinScreen) return Status.SUCCESS;
        if (failures > 40) return Status.FAILED;
        ResourceKey<Level> dim = mc.level.dimension();
        if (lastDim != null && dim != lastDim) {
            BotLog.ok("Nouvelle dimension : " + dim.location().getPath());
            if (dim == Level.NETHER) {
                netherArrival = Bot.feet();
                Waypoints.set("portail_nether", netherArrival);
            }
        }
        lastDim = dim;

        if (dim == Level.END) return go("combat contre le dragon", new Dragon());
        // outil presque casse sans rechange : on en refait un tout de suite (meilleur niveau possible)
        Item tool = ProTools.replacementNeeded();
        if (tool != null) {
            pendingTool = tool;
            return go("nouvel outil : " + tool.getDescription().getString(), new GatherTask(tool, Inv.count(tool) + 1));
        }
        if (dim == Level.NETHER) return nether();
        return overworld();
    }

    // ------------------------------------------------------------------ Nether

    private Status nether() {
        if (netherArrival == null) netherArrival = Bot.feet();
        if (rods() < 7 && rods() + powder() / 2 < 7) return go("batons de blaze (" + rods() + "/7)", new BlazeRods(7));
        int eyesPossible = eyes() + pearls();
        if (eyesPossible < 12 && netherPearlRounds < 3) {
            netherPearlRounds++;
            return go("perles de l'Ender (" + pearls() + "/12)",
                    new KillTask(e -> e instanceof EnderMan, 0, "endermen").until(() -> eyes() + pearls() >= 12));
        }
        if (Bot.feet().distSqr(netherArrival) > 9) return go("retour au portail", new GotoTask(new Goal.Near(netherArrival, 2), 10));
        return go("retour dans le monde normal", new EnterPortal(Blocks.NETHER_PORTAL));
    }

    // ------------------------------------------------------------------ Overworld

    private Status overworld() {
        int eyesPossible = eyes() + Math.min(pearls(), powder() + rods() * 2);
        if (eyesPossible >= 12 || eyes() >= 12) return strongholdPhase();
        if (pearls() >= 12 && rods() + powder() / 2 >= 6) return strongholdPhase();

        // --- preparation avant le Nether
        if (GatherTask.pickTier() < 1) return go("pioche en bois", new GatherTask(Items.WOODEN_PICKAXE, 1));
        if (GatherTask.pickTier() < 2) return go("pioche en pierre", new GatherTask(Items.STONE_PICKAXE, 1));
        if (Inv.count(Items.COBBLESTONE) + Inv.count(Items.COBBLED_DEEPSLATE) < 24)
            return go("pierre", new GatherTask(Items.COBBLESTONE, 32));
        if (food() < 8) return go("nourriture",
                new KillTask(e -> e instanceof Cow || e instanceof Pig || e instanceof Sheep || e instanceof Chicken || e instanceof Rabbit,
                        0, "animaux").until(() -> food() >= 14));
        if (!Inv.has(Items.STONE_SWORD) && !Inv.has(Items.IRON_SWORD) && !Inv.has(Items.DIAMOND_SWORD))
            return go("epee en pierre", new GatherTask(Items.STONE_SWORD, 1));
        if (Inv.count(GatherTask::isLog) < 4 && Inv.count(Items.COAL) < 4)
            return go("combustible", new MineTask(Targets.parse("wood"), 0).until(() -> Inv.count(GatherTask::isLog) >= 6));
        int ironNeeded = (GatherTask.pickTier() >= 3 ? 0 : 3) + (Inv.has(Items.BUCKET) || Inv.has(Items.WATER_BUCKET) ? 0 : 3)
                + (Inv.has(Items.FLINT_AND_STEEL) ? 0 : 1) + (Inv.has(Items.IRON_SWORD) || Inv.has(Items.DIAMOND_SWORD) ? 0 : 2);
        if (ironNeeded > 0 && Inv.count(Items.IRON_INGOT) < ironNeeded)
            return go("fer (" + Inv.count(Items.IRON_INGOT) + "/" + ironNeeded + ")", new GatherTask(Items.IRON_INGOT, ironNeeded));
        if (GatherTask.pickTier() < 3) return go("pioche en fer", new ContainerTasks.Craft(Items.IRON_PICKAXE, 1));
        if (!Inv.has(Items.IRON_SWORD) && !Inv.has(Items.DIAMOND_SWORD)) return go("epee en fer", new ContainerTasks.Craft(Items.IRON_SWORD, 1));
        if (!Inv.has(Items.BUCKET) && !Inv.has(Items.WATER_BUCKET)) return go("seau", new ContainerTasks.Craft(Items.BUCKET, 1));
        // viande crue -> cuite : deux fois plus nourrissante
        Item[] meat = ProTools.meatToCook();
        if (meat != null && Inv.count(meat[1]) < 24) {
            pendingCook = meat[1];
            fr.nathvavan.nathbot.util.Brain.think("nourriture", "J'ai " + Inv.count(meat[0]) + " viandes crues : je les cuis, ca nourrit deux fois plus.");
            return go("cuire la viande", new GatherTask(meat[1], Inv.count(meat[1]) + Inv.count(meat[0])));
        }
        if (GatherTask.pickTier() < 4) {
            if (Inv.count(Items.DIAMOND) < 3) return go("diamants (" + Inv.count(Items.DIAMOND) + "/3)", new GatherTask(Items.DIAMOND, 3));
            return go("pioche en diamant", new ContainerTasks.Craft(Items.DIAMOND_PICKAXE, 1));
        }
        // armure en fer complete avant le Nether (un bot n'esquive pas : l'armure le garde en vie)
        Item piece = ProTools.nextArmorPiece();
        if (piece != null) {
            pendingArmor = piece;
            int need = ProTools.ironFor(piece);
            if (Inv.count(Items.IRON_INGOT) < need)
                return go("fer pour l'armure (" + Inv.count(Items.IRON_INGOT) + "/" + need + ")", new GatherTask(Items.IRON_INGOT, need));
            return go("armure : " + piece.getDescription().getString(), new GatherTask(piece, 1));
        }
        if (!Inv.has(Items.FLINT_AND_STEEL)) {
            if (!Inv.has(Items.FLINT)) return go("silex", new GatherTask(Items.FLINT, 1));
            return go("briquet", new ContainerTasks.Craft(Items.FLINT_AND_STEEL, 1));
        }
        // de quoi faire des ponts au-dessus de la lave dans le Nether
        if (Inv.throwawayCount() < 64 && blockTries < 3) {
            blockTries++;
            return go("blocs pour le Nether (" + Inv.throwawayCount() + "/64)",
                    new GatherTask(Items.COBBLESTONE, Inv.count(Items.COBBLESTONE) + 64 - Inv.throwawayCount()));
        }
        // portail deja construit ?
        BlockPos portal = Scanner.nearest(s -> s.is(Blocks.NETHER_PORTAL), Bot.feet(), 4);
        if (portal != null) {
            overworldPortal = portal;
            Waypoints.set("portail_monde", portal);
            if (!Actions.inReach(portal)) return go("vers le portail", new GotoTask(new Goal.Reach(portal, 3), 6));
            return go("entrer dans le Nether", new EnterPortal(Blocks.NETHER_PORTAL));
        }
        if (portalOrigin != null && isFrameComplete(portalOrigin)) return go("allumer le portail", new LightPortal(portalOrigin));
        if (!Inv.has(Items.WATER_BUCKET) && Inv.count(Items.OBSIDIAN) < 10) return go("remplir le seau d'eau", new FillBucket());
        if (Inv.count(Items.OBSIDIAN) < 10) return go("obsidienne (" + Inv.count(Items.OBSIDIAN) + "/10)", new Obsidian(10));
        if (Inv.count(Items.COBBLESTONE) < 4) return go("pierre", new GatherTask(Items.COBBLESTONE, 8));
        // construire le portail
        if (portalOrigin == null) {
            BlockPos f = Bot.feet();
            Direction d = mc.player.getDirection();
            portalOrigin = f.relative(d, 3);
            portalOrigin = new BlockPos(portalOrigin.getX(), mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, portalOrigin.getX(), portalOrigin.getZ()), portalOrigin.getZ());
            if (Math.abs(portalOrigin.getY() - f.getY()) > 3) portalOrigin = new BlockPos(portalOrigin.getX(), f.getY(), portalOrigin.getZ());
        }
        return go("construire le portail", new BuildTask(Schematics.get("portail_nether"), portalOrigin, false));
    }

    private static boolean isFrameComplete(BlockPos o) {
        for (int x = 1; x <= 2; x++) if (!WV.st(o.offset(x, 0, 0)).is(Blocks.OBSIDIAN) || !WV.st(o.offset(x, 4, 0)).is(Blocks.OBSIDIAN)) return false;
        for (int y = 1; y <= 3; y++) if (!WV.st(o.offset(0, y, 0)).is(Blocks.OBSIDIAN) || !WV.st(o.offset(3, y, 0)).is(Blocks.OBSIDIAN)) return false;
        return true;
    }

    private Status strongholdPhase() {
        if (eyes() < 12) {
            int needEyes = 12 - eyes();
            if (powder() < Math.min(needEyes, pearls())) return go("poudre de blaze", new ContainerTasks.Craft(Items.BLAZE_POWDER, Math.min(needEyes, pearls())));
            if (pearls() > 0 && powder() > 0) return go("yeux de l'Ender", new ContainerTasks.Craft(Items.ENDER_EYE, Math.min(12, eyes() + Math.min(pearls(), powder()))));
        }
        List<BlockPos> frames = Scanner.find(s -> s.is(Blocks.END_PORTAL_FRAME), Bot.feet(), 8, 12, false);
        if (frames.size() >= 12 || !frames.isEmpty() && strongholdEstimate != null && Bot.feet().distSqr(frames.get(0)) < 48 * 48) {
            BlockPos ep = Scanner.nearest(s -> s.is(Blocks.END_PORTAL), Bot.feet(), 8);
            if (ep != null) return go("sauter dans le portail de l'End", new EnterPortal(Blocks.END_PORTAL));
            return go("poser les yeux de l'Ender", new FillFrames());
        }
        if (strongholdEstimate == null) {
            LocateStronghold l = new LocateStronghold(r -> strongholdEstimate = r);
            return go("localiser le fort", l);
        }
        return go("aller au fort", new FindEndPortal(strongholdEstimate));
    }

    // ================================================================== sous-taches

    /** Remplit un seau d'eau. */
    public static class FillBucket extends Task {
        private BlockPos water;
        private int tries;

        @Override
        public String name() { return "Remplir un seau d'eau"; }

        @Override
        protected Status update() {
            if (Inv.has(Items.WATER_BUCKET)) return Status.SUCCESS;
            if (!Inv.has(Items.BUCKET)) return Status.FAILED;
            if (++tries > 40) return Status.FAILED;
            if (water == null || !WV.st(water).getFluidState().isSource()) {
                List<BlockPos> l = Scanner.find(s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource(), Bot.feet(), 6, 30, false);
                l.removeIf(p -> !WV.isAir(p.above()));
                if (l.isEmpty()) {
                    run(new MiscTasks.Explore(null));
                    return Status.RUNNING;
                }
                water = l.get(0);
            }
            if (!Actions.inReach(water) || mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(water)) > 3.5) {
                run(new GotoTask(new Goal.Reach(water, 3.2), 3));
                return Status.RUNNING;
            }
            Inv.select(Items.BUCKET);
            if (Rot.lookAt(Vec3.atCenterOf(water).add(0, 0.4, 0))) Actions.useItem();
            return Status.RUNNING;
        }

        @Override
        protected boolean shouldInterruptChild(Task child) {
            return child instanceof MiscTasks.Explore && ticks % 40 == 0
                    && !Scanner.find(s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource(), Bot.feet(), 6, 1, false).isEmpty();
        }
    }

    /** Obtenir de l'obsidienne : la miner, ou la fabriquer avec de l'eau sur de la lave. */
    public static class Obsidian extends Task {
        private final int need;
        private BlockPos lava;
        private int phase = 0;
        private int wait = 0;

        public Obsidian(int need) { this.need = need; }

        @Override
        public String name() { return "Obsidienne (" + Inv.count(Items.OBSIDIAN) + "/" + need + ")"; }

        @Override
        protected Status update() {
            if (Inv.count(Items.OBSIDIAN) >= need) return Status.SUCCESS;
            List<BlockPos> obs = Scanner.find(s -> s.is(Blocks.OBSIDIAN), Bot.feet(), 6, 40, false);
            if (!obs.isEmpty() && phase == 0) {
                run(new MineTask(Set.of(Blocks.OBSIDIAN), 0, "obsidienne", null).until(() -> Inv.count(Items.OBSIDIAN) >= need
                        || Scanner.find(s -> s.is(Blocks.OBSIDIAN), Bot.feet(), 6, 1, false).isEmpty()));
                return Status.RUNNING;
            }
            if (!Inv.has(Items.WATER_BUCKET) && phase == 0) {
                run(new FillBucket());
                return Status.RUNNING;
            }
            if (phase == 0) {
                List<BlockPos> lv = Scanner.find(s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), Bot.feet(), 6, 60, false);
                lv.removeIf(p -> !WV.isAir(p.above()));
                if (lv.isEmpty()) {
                    status = "recherche de lave";
                    if (Bot.feet().getY() > -30) run(new GotoTask(new Goal.Y(-50), 6));
                    else run(new MiscTasks.Explore(null));
                    return Status.RUNNING;
                }
                lava = lv.get(0);
                phase = 1;
            }
            if (phase == 1) {
                if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(lava)) > 4) {
                    run(new GotoTask(new Goal.Reach(lava, 3.5), 3));
                    return Status.RUNNING;
                }
                Inv.select(Items.WATER_BUCKET);
                if (Rot.lookAt(Vec3.atCenterOf(lava).add(0, 0.45, 0))) {
                    Actions.useItem();
                    phase = 2;
                    wait = 30;
                }
                return Status.RUNNING;
            }
            if (phase == 2) {
                if (wait-- > 0) return Status.RUNNING;
                // recuperer l'eau
                List<BlockPos> w = Scanner.find(s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource(), lava, 1, 5, false);
                w.removeIf(p -> p.distSqr(lava) > 9);
                if (!w.isEmpty() && Inv.has(Items.BUCKET)) {
                    Inv.select(Items.BUCKET);
                    if (Rot.lookAt(Vec3.atCenterOf(w.get(0)).add(0, 0.4, 0))) Actions.useItem();
                    if (ticks % 40 == 0) phase = 0;
                    return Status.RUNNING;
                }
                phase = 0;
            }
            return Status.RUNNING;
        }

        @Override
        protected boolean shouldInterruptChild(Task child) {
            if (ticks % 40 != 0) return false;
            if (child instanceof MiscTasks.Explore || child instanceof GotoTask && phase == 0) {
                return !Scanner.find(s -> s.getFluidState().is(FluidTags.LAVA) && s.getFluidState().isSource(), Bot.feet(), 4, 1, false).isEmpty();
            }
            return false;
        }
    }

    /** Allume le portail au briquet. */
    public static class LightPortal extends Task {
        private final BlockPos origin;

        public LightPortal(BlockPos o) { this.origin = o; }

        @Override
        public String name() { return "Allumer le portail"; }

        @Override
        protected Status update() {
            if (WV.st(origin.offset(1, 1, 0)).is(Blocks.NETHER_PORTAL)) return Status.SUCCESS;
            if (ticks > 400 || !Inv.has(Items.FLINT_AND_STEEL)) return Status.FAILED;
            BlockPos base = origin.offset(1, 0, 0);
            if (!Actions.inReach(base)) {
                run(new GotoTask(new Goal.Reach(base, 3.5), 3));
                return Status.RUNNING;
            }
            Inv.select(Items.FLINT_AND_STEEL);
            if (Rot.lookAt(Vec3.atCenterOf(base).add(0, 0.5, 0))) Actions.useOnBlock(base, Direction.UP);
            return Status.RUNNING;
        }
    }

    /** Entrer dans un portail (Nether ou End). */
    public static class EnterPortal extends Task {
        private final net.minecraft.world.level.block.Block block;
        private ResourceKey<Level> startDim;

        public EnterPortal(net.minecraft.world.level.block.Block b) { this.block = b; }

        @Override
        public String name() { return "Entrer dans le portail"; }

        @Override
        protected Status update() {
            if (startDim == null) startDim = mc.level.dimension();
            if (mc.level.dimension() != startDim) return Status.SUCCESS;
            if (ticks > 20 * 40) return Status.FAILED;
            BlockPos p = Scanner.nearest(s -> s.is(block), Bot.feet(), 3);
            if (p == null) return Status.FAILED;
            BlockPos f = Bot.feet();
            if (WV.st(f).is(block) || WV.st(f.above()).is(block) || WV.st(f.below()).is(block)) {
                status = "attente de la teleportation";
                Bot.I.in.active = true;
                return Status.RUNNING;
            }
            if (Math.sqrt(p.distSqr(f)) > 4) {
                run(new GotoTask(new Goal.Reach(p, 2.5), 4));
                return Status.RUNNING;
            }
            Vec3 c = Vec3.atBottomCenterOf(p);
            Rot.yawTowards(c);
            Bot.I.in.active = true;
            Bot.I.in.forward = 1;
            if (mc.player.horizontalCollision && mc.player.onGround()) Bot.I.in.jump = true;
            return Status.RUNNING;
        }
    }

    /** Trouver un fort du Nether et tuer des blazes. */
    public static class BlazeRods extends Task {
        private final int need;
        private Direction dir;

        public BlazeRods(int need) { this.need = need; }

        @Override
        public String name() { return "Batons de blaze (" + rods() + "/" + need + ")"; }

        @Override
        protected Status update() {
            if (rods() >= need) return Status.SUCCESS;
            List<Entity> blazes = mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(40), e -> e instanceof Blaze && e.isAlive());
            if (!blazes.isEmpty()) {
                run(new KillTask(e -> e instanceof Blaze, 0, "blazes", 40).until(() -> rods() >= need));
                return Status.RUNNING;
            }
            BlockPos spawner = Scanner.nearest(s -> s.is(Blocks.SPAWNER), Bot.feet(), 6);
            if (spawner != null && spawner.distSqr(Bot.feet()) > 25) {
                status = "vers le spawner";
                run(new GotoTask(new Goal.Near(spawner, 4), 6));
                return Status.RUNNING;
            }
            if (spawner != null) {
                status = "attente des blazes";
                run(new MiscTasks.Wait(60));
                return Status.RUNNING;
            }
            BlockPos bricks = Scanner.nearest(s -> s.is(Blocks.NETHER_BRICKS), Bot.feet(), 8);
            if (bricks != null && bricks.distSqr(Bot.feet()) > 36) {
                status = "vers la forteresse";
                run(new GotoTask(new Goal.Near(bricks, 3), 6));
                return Status.RUNNING;
            }
            status = "recherche d'une forteresse";
            if (dir == null) dir = Math.random() < 0.5 ? Direction.SOUTH : Direction.NORTH;
            run(new MiscTasks.Explore(dir));
            return Status.RUNNING;
        }

        @Override
        protected boolean shouldInterruptChild(Task child) {
            if (!(child instanceof MiscTasks.Explore) || ticks % 40 != 0) return false;
            return Scanner.nearest(s -> s.is(Blocks.NETHER_BRICKS), Bot.feet(), 8) != null
                    || !mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(32), e -> e instanceof Blaze).isEmpty();
        }
    }

    /** Lancer deux yeux de l'Ender a des endroits differents et trianguler le fort. */
    public static class LocateStronghold extends Task {
        private final java.util.function.Consumer<BlockPos> result;
        private int phase = 0;
        private Vec3 throwPos, p1, d1;
        private int thrownAt = -1;
        private int eyeId = -1;

        public LocateStronghold(java.util.function.Consumer<BlockPos> r) { this.result = r; }

        @Override
        public String name() { return "Localiser le fort (oeil " + (phase < 2 ? 1 : 2) + ")"; }

        @Override
        protected Status update() {
            if (phase >= 4) return Status.SUCCESS;
            if (!Inv.has(Items.ENDER_EYE)) return Status.FAILED;
            if (phase == 0 || phase == 2) {
                Inv.select(Items.ENDER_EYE);
                Rot.setRotation(mc.player.getYRot(), -20);
                throwPos = mc.player.position();
                Actions.useItem();
                thrownAt = ticks;
                eyeId = -1;
                phase++;
                return Status.RUNNING;
            }
            // suivre l'oeil
            EyeOfEnder eye = null;
            for (Entity e : mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(20), e -> e instanceof EyeOfEnder)) {
                eye = (EyeOfEnder) e;
                break;
            }
            if (ticks - thrownAt < 25) return Status.RUNNING;
            if (eye == null) {
                if (ticks - thrownAt > 80) phase--; // relancer
                return Status.RUNNING;
            }
            Vec3 d = new Vec3(eye.getX() - throwPos.x, 0, eye.getZ() - throwPos.z);
            if (d.length() < 1.5) return Status.RUNNING;
            d = d.normalize();
            if (phase == 1) {
                p1 = throwPos;
                d1 = d;
                phase = 2;
                BotLog.info("Premier oeil : direction " + String.format("%.2f %.2f", d.x, d.z) + ". Je me decale pour trianguler.");
                Vec3 perp = new Vec3(-d.z, 0, d.x).scale(120);
                BlockPos t = BlockPos.containing(throwPos.add(perp).add(d.scale(60)));
                run(new MiscTasks.Sequence("deplacement", List.of(new CollectDropsTask(20, 200, s -> s.is(Items.ENDER_EYE)),
                        new GotoTask(new Goal.XZ(t.getX(), t.getZ()), 10))));
                return Status.RUNNING;
            }
            // phase 3 : intersection
            Vec3 p2 = throwPos;
            double den = d1.x * d.z - d1.z * d.x;
            BlockPos est;
            if (Math.abs(den) < 0.02) {
                est = BlockPos.containing(p2.add(d.scale(800)));
            } else {
                double t = ((p2.x - p1.x) * d.z - (p2.z - p1.z) * d.x) / den;
                Vec3 hit = p1.add(d1.scale(t));
                est = BlockPos.containing(hit.x, 30, hit.z);
            }
            BotLog.ok("Fort estime vers X=" + est.getX() + " Z=" + est.getZ());
            Waypoints.set("fort_estime", est);
            result.accept(est);
            run(new CollectDropsTask(20, 200, s -> s.is(Items.ENDER_EYE)));
            phase = 4;
            return Status.RUNNING;
        }

        @Override
        protected void onChildDone(Task child, Status r) {
            if (phase == 4) phase = 5;
        }

        @Override
        public String describe() {
            return phase >= 4 ? "Fort localise" : super.describe();
        }
    }

    /** Aller jusqu'au fort estime et trouver la salle du portail. */
    public static class FindEndPortal extends Task {
        private final BlockPos est;
        private int spiral = 0;

        public FindEndPortal(BlockPos est) { this.est = est; }

        @Override
        public String name() { return "Chercher le portail de l'End"; }

        @Override
        protected Status update() {
            if (Scanner.nearest(s -> s.is(Blocks.END_PORTAL_FRAME), Bot.feet(), 8) != null) return Status.SUCCESS;
            BlockPos f = Bot.feet();
            double dh = Math.sqrt(Math.pow(f.getX() - est.getX(), 2) + Math.pow(f.getZ() - est.getZ(), 2));
            if (dh > 48) {
                status = (int) dh + " blocs";
                run(new GotoTask(new Goal.XZ(est.getX(), est.getZ()), 12));
                return Status.RUNNING;
            }
            if (f.getY() > 35) {
                status = "on creuse";
                run(new GotoTask(new Goal.Y(28), 8));
                return Status.RUNNING;
            }
            spiral++;
            double a = spiral * 1.1;
            int r = 8 + spiral * 6;
            BlockPos t = est.offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r));
            status = "recherche en spirale " + spiral;
            if (spiral > 30) return Status.FAILED;
            run(new GotoTask(new Goal.Block(t.getX(), f.getY(), t.getZ()), 6));
            return Status.RUNNING;
        }

        @Override
        protected boolean shouldInterruptChild(Task child) {
            return ticks % 40 == 0 && Scanner.nearest(s -> s.is(Blocks.END_PORTAL_FRAME), Bot.feet(), 6) != null;
        }
    }

    /** Placer les yeux de l'Ender dans les cadres. */
    public static class FillFrames extends Task {
        @Override
        public String name() { return "Remplir le portail de l'End"; }

        @Override
        protected Status update() {
            if (Scanner.nearest(s -> s.is(Blocks.END_PORTAL), Bot.feet(), 6) != null) return Status.SUCCESS;
            List<BlockPos> frames = Scanner.find(s -> s.is(Blocks.END_PORTAL_FRAME) && !s.getValue(EndPortalFrameBlock.HAS_EYE), Bot.feet(), 6, 12, false);
            if (frames.isEmpty()) return Status.SUCCESS;
            if (!Inv.has(Items.ENDER_EYE)) {
                BotLog.warn("Plus d'yeux de l'Ender ! Il en manque " + frames.size());
                return Status.FAILED;
            }
            BlockPos fr = frames.get(0);
            if (!Actions.inReach(fr)) {
                run(new GotoTask(new Goal.Reach(fr, 3.5), 4));
                return Status.RUNNING;
            }
            Inv.select(Items.ENDER_EYE);
            if (Rot.lookAt(Vec3.atCenterOf(fr).add(0, 0.4, 0))) Actions.useOnBlock(fr, Direction.UP);
            return Status.RUNNING;
        }
    }

    /** Combat final contre l'Ender Dragon. */
    public static class Dragon extends Task {
        private int bowCharge = 0;

        @Override
        public String name() { return "Combat contre l'Ender Dragon"; }

        @Override
        protected Status update() {
            if (mc.screen instanceof WinScreen) return Status.SUCCESS;
            if (mc.level.dimension() != Level.END) return Status.SUCCESS;
            List<Entity> dragons = mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(300), e -> e instanceof EnderDragon && e.isAlive());
            if (dragons.isEmpty()) {
                BlockPos exit = Scanner.nearest(s -> s.is(Blocks.END_PORTAL), Bot.feet(), 6);
                if (exit != null && ticks > 200) {
                    BotLog.ok("Dragon vaincu ! Retour par le portail de sortie.");
                    run(new EnterPortal(Blocks.END_PORTAL));
                    return Status.RUNNING;
                }
                status = "dragon introuvable";
                return Status.RUNNING;
            }
            EnderDragon dragon = (EnderDragon) dragons.get(0);
            // 1) cristaux a l'arc si possible
            boolean bow = Inv.has(Items.BOW) && Inv.count(s -> s.is(ItemTags.ARROWS)) > 0;
            List<Entity> crystals = mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(80), e -> e instanceof EndCrystal && e.isAlive());
            if (bow && !crystals.isEmpty()) {
                Entity c = crystals.stream().min((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player))).get();
                Inv.select(Items.BOW);
                double dist = c.distanceTo(mc.player);
                Vec3 aim = c.position().add(0, 0.5 + dist * dist * 0.0012, 0);
                Rot.lookAt(aim);
                if (bowCharge == 0) mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                Bot.I.requestUseKey();
                if (++bowCharge > 22) {
                    Rot.syncNow();
                    mc.gameMode.releaseUsingItem(mc.player);
                    bowCharge = 0;
                }
                status = "tir sur les cristaux (" + crystals.size() + ")";
                return Status.RUNNING;
            }
            // 2) attendre le dragon pres de la fontaine et frapper
            int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            BlockPos fountain = new BlockPos(0, top, 0);
            double d = Math.sqrt(dragon.distanceToSqr(mc.player));
            if (d < 10) {
                run(new KillTask(e -> e instanceof EnderDragon, 1, "Ender Dragon", 20));
                return Status.RUNNING;
            }
            if (Bot.feet().distSqr(fountain) > 36) {
                run(new GotoTask(new Goal.Near(fountain.offset(3, 0, 0), 2), 8));
                return Status.RUNNING;
            }
            status = "attente du dragon sur la fontaine";
            Rot.lookAt(dragon.position());
            return Status.RUNNING;
        }

        @Override
        protected boolean shouldInterruptChild(Task child) {
            return child instanceof KillTask && ticks % 20 == 0 && mc.level.getEntities(mc.player,
                    new AABB(mc.player.blockPosition()).inflate(14), e -> e instanceof EnderDragon).isEmpty();
        }
    }
}
