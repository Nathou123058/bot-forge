package fr.nathvavan.nathbot.bot;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.path.Path;
import fr.nathvavan.nathbot.task.Task;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Cerveau central du bot : tache en cours, entrees, statistiques. */
public final class Bot {
    public static final Bot I = new Bot();

    public final BotInput in = new BotInput();
    public final Stats stats = new Stats();
    public final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();

    private Task task;
    /** Tache d'urgence (reflexe de survie) qui passe avant la tache principale, puis lui rend la main. */
    private Task emergency;
    private int lastEmergencyEnd = -100000;
    public boolean paused;
    /** Une fonction (manger, MLG...) a pris la main ce tick : la tache attend. */
    public boolean featureBusy;

    public volatile Path renderPath;
    public volatile int renderIdx;

    private boolean attackRequested, useRequested;
    private boolean attackHeld, useHeld;
    private BlockPos breakingPos;
    private net.minecraft.world.level.block.state.BlockState breakingState;

    private Bot() {}

    public static BlockPos feet() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return BlockPos.ZERO;
        return BlockPos.containing(p.getX(), Math.floor(p.getY() + 0.5), p.getZ());
    }

    public Task task() { return task; }

    public boolean isActive() { return task != null && !paused; }

    public String statusText() {
        String dev = fr.nathvavan.nathbot.dev.DevMode.locked() ? "[Claude code le bot] " : "";
        if (task == null) return dev + "Inactif";
        if (emergency != null) return dev + "[URGENCE] " + emergency.describe() + " (puis : " + task.name() + ")";
        return dev + (paused ? "[PAUSE] " : "") + task.describe();
    }

    public void start(Task t) {
        if (fr.nathvavan.nathbot.dev.DevMode.locked() && !fr.nathvavan.nathbot.dev.DevMode.bypass) {
            fr.nathvavan.nathbot.dev.DevMode.refuse();
            return;
        }
        stopTaskOnly();
        task = t;
        paused = false;
        stats.tasksStarted++;
        BotLog.info("Tache : " + t.name());
    }

    private void stopTaskOnly() {
        if (task != null) {
            try {
                task.stop();
            } catch (Throwable ignored) {}
        }
        task = null;
        if (emergency != null) {
            try { emergency.stop(); } catch (Throwable ignored) {}
        }
        emergency = null;
        renderPath = null;
        WV.protectedPositions = java.util.Set.of();
    }

    public void stop() {
        boolean had = task != null;
        if (Features.isOn("auto_walk")) Features.set("auto_walk", false);
        stopTaskOnly();
        releaseKeys();
        in.reset();
        if (had) BotLog.warn("Bot arrete.");
    }

    public void requestAttackKey() { attackRequested = true; }

    public void requestUseKey() { useRequested = true; }

    void markBreaking(BlockPos p) {
        if (!p.equals(breakingPos)) {
            breakingPos = p;
            breakingState = WV.st(p);
        }
    }

    /** Appele a la fin de chaque tick client. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        String cmd;
        while ((cmd = commandQueue.poll()) != null) Commands.execute(cmd);

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (task != null) stop();
            return;
        }
        fr.nathvavan.nathbot.dev.DevMode.tick();
        attackRequested = false;
        useRequested = false;
        featureBusy = false;
        in.reset();
        Actions.tickCooldowns();
        stats.tick(p);

        // bloc en cours de minage termine ?
        if (breakingPos != null && WV.passable(breakingPos)) {
            if (breakingState != null && !breakingState.isAir()) stats.blockMined(breakingState);
            breakingPos = null;
        }

        Features.tickAll();

        // securite : vie basse
        int panic = BotConfig.get(BotConfig.PANIC_HEALTH, 5);
        float hp = p.getHealth();
        if (task != null && !paused && emergency == null && hp > 0) {
            boolean reflex = Features.isOn("survival_reflex");
            if (reflex && (hp <= Math.max(8, panic) && p.tickCount - lastEmergencyEnd > 20 * 30 || panic > 0 && hp <= panic)) {
                // au lieu de rester plante : se mettre a l'abri, se soigner, puis reprendre
                emergency = new fr.nathvavan.nathbot.task.Refuge();
                releaseKeys();
                BotLog.warn("Vie basse (" + (int) hp + ") : je me mets a l'abri pour me soigner, puis je reprends.");
            } else if (!reflex && panic > 0 && hp <= panic) {
                paused = true;
                BotLog.error("Vie basse (" + (int) hp + ") : bot en pause. Tape '/nb resume' pour reprendre.");
            }
        }
        if (emergency != null && !paused && !featureBusy && p.isAlive()) {
            Task.Status es;
            try {
                es = emergency.tick();
            } catch (Throwable t) {
                fr.nathvavan.nathbot.NathBot.LOG.error("Erreur dans l'urgence", t);
                es = Task.Status.FAILED;
            }
            if (es != Task.Status.RUNNING) {
                emergency = null;
                lastEmergencyEnd = p.tickCount;
                if (es == Task.Status.SUCCESS) BotLog.ok("Remis sur pied, je reprends : " + (task != null ? task.name() : "rien"));
            }
            applyKeys(mc);
            return;
        }

        if (task != null && !paused && !featureBusy && p.isAlive()) {
            Task.Status s;
            try {
                s = task.tick();
            } catch (Throwable t) {
                fr.nathvavan.nathbot.NathBot.LOG.error("Erreur dans la tache", t);
                BotLog.error("Erreur : " + t);
                s = Task.Status.FAILED;
            }
            if (s != Task.Status.RUNNING) {
                if (s == Task.Status.SUCCESS) BotLog.ok("Termine : " + task.name());
                else BotLog.error("Echec : " + task.name());
                stats.tasksDone++;
                stopTaskOnly();
            }
        }
        applyKeys(mc);
        if (in.active && in.sprint && in.forward > 0 && p.getFoodData().getFoodLevel() > 6) p.setSprinting(true);
    }

    private void applyKeys(Minecraft mc) {
        if (attackRequested) {
            mc.options.keyAttack.setDown(true);
            attackHeld = true;
        } else if (attackHeld) {
            mc.options.keyAttack.setDown(false);
            attackHeld = false;
        }
        if (useRequested) {
            mc.options.keyUse.setDown(true);
            useHeld = true;
        } else if (useHeld) {
            mc.options.keyUse.setDown(false);
            useHeld = false;
        }
    }

    public void releaseKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (attackHeld) mc.options.keyAttack.setDown(false);
        if (useHeld) mc.options.keyUse.setDown(false);
        attackHeld = useHeld = false;
    }
}
