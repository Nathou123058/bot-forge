package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.Targets;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Minage automatique : repere les blocs voulus dans les chunks charges, y va, les mine,
 * ramasse. S'il n'en voit pas, descend a la bonne couche et creuse des galeries (branch mining).
 */
public class MineTask extends Task {
    private final Set<Block> targets;
    private final int count;
    private final String label;
    private final Integer bestY;
    private BooleanSupplier until;

    private int mined = 0;
    private List<BlockPos> known = new ArrayList<>();
    private final Set<BlockPos> watched = new HashSet<>();
    private final Set<Long> blacklist = new HashSet<>();
    private int scanCooldown = 0;
    private Direction branchDir;
    private int branchSegments = 0;
    private boolean exploring = false;
    private int noTargetFails = 0;

    public MineTask(Targets.Spec spec, int count) {
        this(spec.blocks(), count, spec.label(), spec.bestY());
    }

    public MineTask(Set<Block> targets, int count, String label, Integer bestY) {
        this.targets = targets;
        this.count = count;
        this.label = label;
        this.bestY = bestY;
    }

    /** Condition d'arret alternative (ex. "avoir 3 diamants dans l'inventaire"). */
    public MineTask until(BooleanSupplier s) {
        this.until = s;
        return this;
    }

    @Override
    public String name() { return "Miner " + label + " (" + mined + "/" + (count > 0 ? count : "?") + ")"; }

    private boolean isTarget(BlockPos p) {
        return targets.contains(WV.st(p).getBlock());
    }

    private void rescan() {
        boolean legit = BotConfig.get(BotConfig.LEGIT_MINING, false);
        int r = BotConfig.get(BotConfig.SCAN_RADIUS_CHUNKS, 6);
        List<BlockPos> found = Scanner.find(s -> targets.contains(s.getBlock()), Bot.feet(), r, 200, legit);
        found.removeIf(p -> blacklist.contains(p.asLong()));
        known = found;
        watched.addAll(found.subList(0, Math.min(found.size(), 40)));
        scanCooldown = 40;
    }

    private void checkWatched() {
        BlockPos feet = Bot.feet();
        Iterator<BlockPos> it = watched.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!WV.loaded(p)) {
                it.remove();
                continue;
            }
            if (!isTarget(p)) {
                if (p.distSqr(feet) < 64) mined++;
                it.remove();
            }
        }
    }

    private boolean finished() {
        if (until != null && until.getAsBoolean()) return true;
        return count > 0 && mined >= count;
    }

    @Override
    protected Status update() {
        checkWatched();
        if (finished()) return Status.SUCCESS;
        if (targets.isEmpty()) {
            BotLog.error("Bloc inconnu : " + label);
            return Status.FAILED;
        }
        if (scanCooldown-- <= 0 || known.isEmpty()) rescan();
        known.removeIf(p -> !isTarget(p) || blacklist.contains(p.asLong()));
        if (!known.isEmpty()) {
            exploring = false;
            List<Goal> goals = new ArrayList<>();
            for (int i = 0; i < Math.min(6, known.size()); i++) goals.add(new Goal.TwoBlocks(known.get(i)));
            GotoTask g = new GotoTask(new Goal.Any(goals), 3);
            g.quiet = true;
            status = "vers " + known.get(0).toShortString();
            run(g);
            return Status.RUNNING;
        }
        // rien en vue : on explore
        exploring = true;
        BlockPos feet = Bot.feet();
        Integer y = bestY;
        if (y != null && mc.level.dimension() != Level.OVERWORLD && !(label.contains("quartz") || label.contains("debris") || label.contains("netherite"))) y = null;
        if (y != null && Math.abs(feet.getY() - y) > 3) {
            status = "descend a Y=" + y;
            run(new GotoTask(new Goal.Y(y), 6));
            return Status.RUNNING;
        }
        if (branchDir == null) branchDir = mc.player.getDirection();
        if (branchSegments > 0 && branchSegments % 3 == 0) branchDir = branchDir.getClockWise();
        branchSegments++;
        BlockPos target = feet.relative(branchDir, 24);
        if (y != null) target = new BlockPos(target.getX(), y, target.getZ());
        status = "galerie vers " + branchDir.getName();
        run(new GotoTask(new Goal.Block(target), 4));
        return Status.RUNNING;
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        if (child instanceof GotoTask && ticks % 20 == 0) {
            checkWatched();
            if (finished()) return true;
            if (exploring) {
                rescan();
                known.removeIf(p -> !isTarget(p));
                return !known.isEmpty();
            }
        }
        return false;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        checkWatched();
        if (child instanceof GotoTask g && !exploring) {
            if (result == Status.FAILED && !known.isEmpty()) {
                // inaccessible : on l'oublie
                BlockPos bad = known.get(0);
                blacklist.add(bad.asLong());
                known.remove(0);
                if (++noTargetFails > 25) BotLog.warn("Beaucoup de blocs inaccessibles...");
            } else if (result == Status.SUCCESS) {
                run(new CollectDropsTask(7, 60));
            }
        } else if (child instanceof GotoTask && exploring && result == Status.FAILED) {
            branchDir = branchDir == null ? Direction.NORTH : branchDir.getClockWise();
        }
    }
}
