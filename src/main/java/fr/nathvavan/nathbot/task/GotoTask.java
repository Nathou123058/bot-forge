package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.AStar;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.path.Path;
import fr.nathvavan.nathbot.path.PathExecutor;
import fr.nathvavan.nathbot.path.PathService;
import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/** Aller vers un objectif, en recalculant le chemin par morceaux. */
public class GotoTask extends Task {
    private final Goal goal;
    private PathService.Search search;
    private PathExecutor exec;
    private int fails = 0;
    private int wait = 0;
    private int segments = 0;
    private final int maxFails;
    public boolean quiet = false;
    private Consumer<AStar.Ctx> ctxTweak;
    private int boatCheck = 0;

    public GotoTask(Goal goal) {
        this(goal, 8);
    }

    public GotoTask(Goal goal, int maxFails) {
        this.goal = goal;
        this.maxFails = maxFails;
    }

    public GotoTask tweak(Consumer<AStar.Ctx> c) {
        this.ctxTweak = c;
        return this;
    }

    public Goal goal() { return goal; }

    @Override
    public String name() { return goal.describe(); }

    @Override
    protected Status update() {
        BlockPos feet = Bot.feet();
        if (goal.isAt(feet) && (mc.player.onGround() || mc.player.isInWater())) {
            Bot.I.renderPath = null;
            return Status.SUCCESS;
        }
        if (exec != null) {
            PathExecutor.Result r = exec.tick();
            if (r == PathExecutor.Result.RUNNING) return Status.RUNNING;
            if (r == PathExecutor.Result.FAILED) {
                fails++;
                if (!quiet) BotLog.quiet("Chemin interrompu : " + exec.failReason);
                wait = 5;
            } else if (segments < 400) {
                fails = 0;
            }
            exec = null;
            return fails > maxFails ? Status.FAILED : Status.RUNNING;
        }
        if (wait > 0) {
            wait--;
            return Status.RUNNING;
        }
        if (search == null) {
            if (!mc.player.onGround() && !mc.player.isInWater() && !mc.player.onClimbable()) return Status.RUNNING;
            if (boatCheck-- <= 0) {
                boatCheck = 60;
                int[] t = targetXZ();
                if (t != null && BoatTask.worthIt(t[0], t[1])) {
                    fr.nathvavan.nathbot.util.Brain.think("eau", "Beaucoup d'eau entre moi et la destination : plus rapide en bateau.");
                    run(new BoatTask(t[0], t[1]));
                    return Status.RUNNING;
                }
            }
            AStar.Ctx ctx = PathService.defaultCtx();
            if (ctxTweak != null) ctxTweak.accept(ctx);
            search = PathService.start(feet, goal, ctx);
            status = "calcul...";
            return Status.RUNNING;
        }
        if (!search.done()) return Status.RUNNING;
        Path p = search.result();
        search = null;
        if (p == null || p.steps.isEmpty()) {
            fails++;
            status = "aucun chemin (" + fails + ")";
            wait = 10;
            if (fails > maxFails) {
                if (!quiet) BotLog.warn("Impossible de trouver un chemin : " + goal.describe());
                return Status.FAILED;
            }
            return Status.RUNNING;
        }
        segments++;
        status = (p.partial ? "segment " + segments : "chemin") + ", " + p.steps.size() + " pas";
        exec = new PathExecutor(p);
        Bot.I.renderPath = p;
        Bot.I.renderIdx = 0;
        return Status.RUNNING;
    }

    /** Destination X/Z si l'objectif en a une. */
    private int[] targetXZ() {
        if (goal instanceof Goal.Block b) return new int[]{b.x(), b.z()};
        if (goal instanceof Goal.XZ g) return new int[]{g.x(), g.z()};
        if (goal instanceof Goal.Near n) return new int[]{n.x(), n.z()};
        return null;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        if (child instanceof BoatTask && result == Status.FAILED) boatCheck = 20 * 120; // on nage, tant pis
    }

    @Override
    protected void onStop() {
        if (search != null) search.cancel();
        search = null;
        exec = null;
        Bot.I.renderPath = null;
    }
}
