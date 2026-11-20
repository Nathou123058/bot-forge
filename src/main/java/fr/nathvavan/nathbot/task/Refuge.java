package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.util.Brain;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Reflexe de survie (vie basse) : au lieu de rester plante (et de se faire tuer par un squelette),
 * le bot creuse un trou de 2 blocs sous ses pieds, se couvre avec un bloc, et attend d'etre soigne
 * (la fonction "Manger automatiquement" le nourrit pour qu'il regenere). Puis il reprend sa tache.
 */
public class Refuge extends Task {
    private int phase = 0; // 0 decider, 1 creuser, 2 se couvrir, 3 attendre
    private BlockPos start;
    private int phaseTicks = 0;

    @Override
    public String name() { return "Se mettre a l'abri (vie basse)"; }

    private void to(int ph) {
        phase = ph;
        phaseTicks = 0;
    }

    /** On peut creuser ici sans tomber dans la lave ni dans un trou ? */
    private boolean canDig(BlockPos f) {
        for (int d = 1; d <= 2; d++) {
            BlockPos b = f.below(d);
            if (WV.passable(b) || WV.isWater(b) || WV.isLava(b)) return false;
            if (WV.breakCost(b, true, Inv.toolSnapshot()) >= WV.INF) return false;
            for (Direction dir : Direction.Plane.HORIZONTAL) if (WV.isLava(b.relative(dir)) || WV.isWater(b.relative(dir))) return false;
        }
        BlockPos floor = f.below(3);
        return WV.solidSupport(floor) && !WV.isLava(floor.below());
    }

    @Override
    protected Status update() {
        LocalPlayer p = mc.player;
        phaseTicks++;
        float max = p.getMaxHealth();
        if (p.getHealth() >= Math.min(16f, max - 2) || ticks > 20 * 120) {
            Brain.think("securite", "Vie remontee a " + (int) p.getHealth() + " : je reprends ma tache.");
            return Status.SUCCESS;
        }
        switch (phase) {
            case 0 -> {
                if (!p.onGround() && !p.isInWater()) return Status.RUNNING;
                start = Bot.feet();
                if (!p.isInWater() && !p.isInLava() && canDig(start) && Inv.throwawayCount() > 0) {
                    Brain.think("securite", "Vie basse (" + (int) p.getHealth() + ") : je creuse un trou et je me couvre pour me soigner.");
                    to(1);
                } else {
                    Brain.think("securite", "Vie basse (" + (int) p.getHealth() + ") : pas d'abri possible ici, je mange et je me defends.");
                    to(3);
                }
            }
            case 1 -> {
                BlockPos f = Bot.feet();
                if (f.getY() <= start.getY() - 2 && p.onGround()) { to(2); return Status.RUNNING; }
                if (phaseTicks > 20 * 15) { to(3); return Status.RUNNING; }
                Actions.breakBlock(f.below());
            }
            case 2 -> {
                BlockPos cap = Bot.feet().above(2);
                if (!WV.replaceable(cap) || phaseTicks > 20 * 5 || Inv.throwawayCount() <= 0) { to(3); return Status.RUNNING; }
                Actions.place(cap, Inv::isThrowaway);
            }
            default -> status = "je me soigne (" + (int) p.getHealth() + "/" + (int) max + ")";
        }
        return Status.RUNNING;
    }
}
