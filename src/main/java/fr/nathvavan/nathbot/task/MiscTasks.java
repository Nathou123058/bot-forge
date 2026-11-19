package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Petites taches : ferme, exploration, tunnel, surface, dormir, attendre. */
public final class MiscTasks {
    private MiscTasks() {}

    // ===================================================================== ferme

    public static class Farm extends Task {
        private final int radius;
        private final BlockPos center;
        private BlockPos replant;
        private Item seed;
        private final Set<Long> ignore = new HashSet<>();
        private int idle = 0;

        public Farm(int radius) {
            this.radius = radius;
            this.center = Bot.feet();
        }

        @Override
        public String name() { return "Ferme automatique (rayon " + radius + ")"; }

        public static boolean mature(BlockState s) {
            Block b = s.getBlock();
            if (b instanceof CropBlock c) return c.isMaxAge(s);
            if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
            if (b instanceof CocoaBlock) return s.getValue(CocoaBlock.AGE) >= 2;
            return b == Blocks.PUMPKIN || b == Blocks.MELON;
        }

        public static Item seedFor(Block b) {
            if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
            if (b == Blocks.CARROTS) return Items.CARROT;
            if (b == Blocks.POTATOES) return Items.POTATO;
            if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
            if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
            return null;
        }

        @Override
        protected Status update() {
            if (replant != null) {
                BlockPos soil = replant.below();
                if (!WV.isAir(replant) || seed == null || !Inv.has(seed)) {
                    replant = null;
                    return Status.RUNNING;
                }
                if (!Actions.inReach(soil)) {
                    run(new GotoTask(new Goal.Reach(soil, 3.5), 2));
                    return Status.RUNNING;
                }
                final Item s = seed;
                Inv.select(st -> st.is(s), 2);
                if (Rot.lookAt(Vec3.atCenterOf(soil).add(0, 0.5, 0))) {
                    Actions.useOnBlock(soil, Direction.UP);
                    replant = null;
                }
                return Status.RUNNING;
            }
            List<BlockPos> crops = Scanner.find(s -> mature(s), center, Math.max(1, radius / 16 + 1), 64, false);
            crops.removeIf(p -> p.distSqr(center) > radius * radius || ignore.contains(p.asLong()));
            if (crops.isEmpty()) {
                status = "attente que ca pousse";
                if (++idle % 200 == 0) run(new CollectDropsTask(radius, 200));
                return Status.RUNNING;
            }
            crops.sort((a, b) -> Double.compare(a.distSqr(Bot.feet()), b.distSqr(Bot.feet())));
            BlockPos c = crops.get(0);
            if (!Actions.inReach(c)) {
                GotoTask g = new GotoTask(new Goal.Reach(c, 3.5), 2);
                g.quiet = true;
                run(g);
                return Status.RUNNING;
            }
            Block b = WV.st(c).getBlock();
            if (Actions.breakBlock(c) || WV.isAir(c)) {
                seed = seedFor(b);
                if (seed != null) replant = c;
                run(new CollectDropsTask(4, 40));
            }
            status = "recolte";
            return Status.RUNNING;
        }

        @Override
        protected void onChildDone(Task child, Status result) {
            if (child instanceof GotoTask && result == Status.FAILED && replant == null) {
                List<BlockPos> crops = Scanner.find(Farm::mature, Bot.feet(), 1, 1, false);
                if (!crops.isEmpty()) ignore.add(crops.get(0).asLong());
            }
        }
    }

    // ===================================================================== exploration

    public static class Explore extends Task {
        private Direction dir;
        private int legs = 0;

        public Explore(Direction dir) {
            this.dir = dir;
        }

        @Override
        public String name() { return "Explorer vers " + (dir == null ? "?" : dir.getName()); }

        @Override
        protected Status update() {
            if (dir == null) dir = mc.player.getDirection();
            BlockPos f = Bot.feet();
            BlockPos t = f.relative(dir, 64);
            legs++;
            run(new GotoTask(new Goal.XZ(t.getX(), t.getZ()), 6));
            return Status.RUNNING;
        }

        @Override
        protected void onChildDone(Task child, Status result) {
            if (result == Status.FAILED) dir = legs % 2 == 0 ? dir.getClockWise() : dir.getCounterClockWise();
        }
    }

    // ===================================================================== tunnel

    public static class Tunnel extends Task {
        private final int length;
        private BlockPos start;
        private Direction dir;

        public Tunnel(int length) {
            this.length = length;
        }

        @Override
        public String name() { return "Creuser un tunnel de " + length + " blocs"; }

        @Override
        protected Status update() {
            if (start == null) {
                start = Bot.feet();
                dir = mc.player.getDirection();
                run(new GotoTask(new Goal.Block(start.relative(dir, length)), 6).tweak(c -> {
                    c.allowPlace = true;
                }));
                return Status.RUNNING;
            }
            return Status.SUCCESS;
        }
    }

    // ===================================================================== surface

    public static class Surface extends Task {
        @Override
        public String name() { return "Remonter a la surface"; }

        @Override
        protected Status update() {
            BlockPos f = Bot.feet();
            int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.getX(), f.getZ());
            if (f.getY() >= top) return Status.SUCCESS;
            if (ticks > 20 * 120) return Status.FAILED;
            run(new GotoTask(new Goal.Block(f.getX(), top, f.getZ()), 8));
            return Status.RUNNING;
        }
    }

    // ===================================================================== dormir

    public static class Sleep extends Task {
        private BlockPos bed;
        private int tries;

        @Override
        public String name() { return "Aller dormir"; }

        @Override
        protected Status update() {
            if (mc.player.isSleeping()) return ticks > 20 * 15 || mc.level.isDay() ? Status.SUCCESS : Status.RUNNING;
            if (mc.level.isDay() && ticks > 5) return Status.SUCCESS;
            if (bed == null) bed = Scanner.nearest(s -> s.getBlock() instanceof BedBlock, Bot.feet(), 3);
            if (bed == null) {
                if (Inv.count(s -> s.getItem() instanceof net.minecraft.world.item.BedItem) > 0) {
                    BotLog.info("Je pose mon lit.");
                    Inv.select(s -> s.getItem() instanceof net.minecraft.world.item.BedItem, 2);
                    BlockPos f = Bot.feet();
                    Direction d = mc.player.getDirection();
                    BlockPos p = f.relative(d, 2);
                    if (WV.replaceable(p) && WV.replaceable(p.relative(d))) Actions.placeAgainst(p.below(), Direction.UP, s -> s.getItem() instanceof net.minecraft.world.item.BedItem);
                    if (++tries > 40) return Status.FAILED;
                    return Status.RUNNING;
                }
                BotLog.warn("Pas de lit a proximite.");
                return Status.FAILED;
            }
            if (!Actions.inReach(bed)) {
                run(new GotoTask(new Goal.Reach(bed, 3), 3));
                return Status.RUNNING;
            }
            Actions.useOnBlock(bed, Direction.UP);
            if (++tries > 30) return Status.FAILED;
            return Status.RUNNING;
        }
    }

    // ===================================================================== attendre

    public static class Wait extends Task {
        private final int duration;

        public Wait(int ticks) { this.duration = ticks; }

        @Override
        public String name() { return "Attendre"; }

        @Override
        protected Status update() { return ticks >= duration ? Status.SUCCESS : Status.RUNNING; }
    }

    /** Execute plusieurs taches a la suite (macro). */
    public static class Sequence extends Task {
        private final List<Task> tasks;
        private int i = 0;
        private final String label;

        public Sequence(String label, List<Task> tasks) {
            this.label = label;
            this.tasks = tasks;
        }

        @Override
        public String name() { return label + " (" + Math.min(i + 1, tasks.size()) + "/" + tasks.size() + ")"; }

        @Override
        protected Status update() {
            if (i >= tasks.size()) return Status.SUCCESS;
            run(tasks.get(i++));
            return Status.RUNNING;
        }
    }
}
