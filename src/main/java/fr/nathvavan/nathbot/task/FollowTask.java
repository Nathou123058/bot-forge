package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.path.Goal;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Suivre un joueur (ou le joueur le plus proche). */
public class FollowTask extends Task {
    private final String target;
    private BlockPos lastGoal;
    private final boolean once;

    public FollowTask(String target, boolean once) {
        this.target = target;
        this.once = once;
    }

    @Override
    public String name() { return (once ? "Venir vers " : "Suivre ") + (target == null ? "le joueur le plus proche" : target); }

    private Entity find() {
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (AbstractClientPlayer pl : mc.level.players()) {
            if (pl == mc.player) continue;
            if (target != null && !pl.getGameProfile().getName().equalsIgnoreCase(target)) continue;
            double d = pl.distanceToSqr(mc.player);
            if (d < bd) { bd = d; best = pl; }
        }
        return best;
    }

    @Override
    protected Status update() {
        Entity e = find();
        if (e == null) {
            status = "joueur introuvable";
            return ticks > 100 ? Status.FAILED : Status.RUNNING;
        }
        BlockPos p = e.blockPosition();
        if (mc.player.distanceTo(e) < 3) {
            if (once) return Status.SUCCESS;
            status = "a cote";
            return Status.RUNNING;
        }
        lastGoal = p;
        GotoTask g = new GotoTask(new Goal.Near(p, 2), 3);
        g.quiet = true;
        run(g);
        return Status.RUNNING;
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        if (ticks % 10 != 0) return false;
        Entity e = find();
        return e != null && lastGoal != null && e.blockPosition().distSqr(lastGoal) > 16;
    }
}
