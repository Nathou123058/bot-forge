package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Chasser des creatures (vaches pour manger, blazes, endermen, dragon...). */
public class KillTask extends Task {
    private final Predicate<Entity> filter;
    private final int count;
    private final String label;
    private BooleanSupplier until;
    private int kills = 0;
    private LivingEntity current;
    private boolean hitCurrent;
    private BlockPos lastGoal;
    private int searchFails = 0;
    private Direction wander;
    private final double range;

    public KillTask(Predicate<Entity> filter, int count, String label) {
        this(filter, count, label, 64);
    }

    public KillTask(Predicate<Entity> filter, int count, String label, double range) {
        this.filter = filter;
        this.count = count;
        this.label = label;
        this.range = range;
    }

    public KillTask until(BooleanSupplier s) {
        this.until = s;
        return this;
    }

    @Override
    public String name() { return "Chasser " + label + " (" + kills + "/" + (count > 0 ? count : "?") + ")"; }

    private LivingEntity find() {
        List<Entity> l = mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(range),
                e -> e instanceof LivingEntity le && le.isAlive() && filter.test(e));
        l.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        return l.isEmpty() ? null : (LivingEntity) l.get(0);
    }

    private boolean done() {
        if (until != null && until.getAsBoolean()) return true;
        return count > 0 && kills >= count;
    }

    @Override
    protected Status update() {
        if (current != null && !current.isAlive()) {
            if (hitCurrent) {
                kills++;
                Bot.I.stats.kills++;
            }
            current = null;
            hitCurrent = false;
            run(new CollectDropsTask(6, 80));
            return Status.RUNNING;
        }
        if (done()) return Status.SUCCESS;
        if (current == null || !current.isAlive()) current = find();
        if (current == null) {
            status = "aucune cible, exploration";
            if (++searchFails > 30) return Status.FAILED;
            if (wander == null) wander = mc.player.getDirection();
            else wander = wander.getClockWise();
            GotoTask g = new GotoTask(new Goal.Near(Bot.feet().relative(wander, 30), 4), 3);
            g.quiet = true;
            run(g);
            return Status.RUNNING;
        }
        searchFails = 0;
        Entity hitTarget = current;
        double dist = mc.player.distanceTo(current);
        if (current instanceof EnderDragon dragon) {
            // on vise la partie du dragon la plus proche
            double best = Double.MAX_VALUE;
            for (EnderDragonPart part : dragon.getSubEntities()) {
                double d = part.distanceTo(mc.player);
                if (d < best) { best = d; hitTarget = part; }
            }
            dist = best;
        }
        if (dist <= 3.4) {
            Inv.selectBestWeapon();
            if (Actions.attack(hitTarget)) hitCurrent = true;
            Bot.I.in.active = true;
            return Status.RUNNING;
        }
        BlockPos p = current.blockPosition();
        lastGoal = p;
        status = "poursuite";
        GotoTask g = new GotoTask(new Goal.Near(p, 2), 2);
        g.quiet = true;
        run(g);
        return Status.RUNNING;
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        if (child instanceof CollectDropsTask) return false;
        if (current == null) {
            if (ticks % 20 == 0) {
                current = find();
                return current != null;
            }
            return false;
        }
        if (!current.isAlive()) return true;
        if (mc.player.distanceTo(current) <= 3.2) return true;
        return ticks % 10 == 0 && lastGoal != null && current.blockPosition().distSqr(lastGoal) > 9;
    }
}
