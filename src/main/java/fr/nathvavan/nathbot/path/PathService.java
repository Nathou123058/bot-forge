package fr.nathvavan.nathbot.path;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.NathBot;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.core.BlockPos;

import java.util.concurrent.*;

/** Lance les calculs de chemin sur un thread separe pour ne pas faire laguer le jeu. */
public final class PathService {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NathBot-Pathfinder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    private PathService() {}

    public static final class Search {
        private final AStar astar;
        private final Future<Path> future;

        Search(AStar a) {
            this.astar = a;
            this.future = EXEC.submit(() -> {
                try {
                    return a.compute();
                } catch (Throwable t) {
                    NathBot.LOG.error("Erreur pathfinding", t);
                    return null;
                }
            });
        }

        public boolean done() { return future.isDone(); }

        public Path result() {
            try {
                return future.get();
            } catch (Exception e) {
                return null;
            }
        }

        public void cancel() {
            astar.cancel();
            future.cancel(false);
        }
    }

    public static AStar.Ctx defaultCtx() {
        AStar.Ctx c = new AStar.Ctx();
        c.allowBreak = BotConfig.get(BotConfig.ALLOW_BREAK, true);
        c.allowPlace = BotConfig.get(BotConfig.ALLOW_PLACE, true);
        c.allowDigDown = BotConfig.get(BotConfig.ALLOW_DIG_DOWN, true);
        c.maxFall = BotConfig.get(BotConfig.MAX_FALL, 3);
        c.timeoutMs = BotConfig.get(BotConfig.PATH_TIMEOUT_MS, 2500);
        c.throwaway = Inv.throwawayCount();
        c.tools = Inv.toolSnapshot();
        return c;
    }

    public static Search start(BlockPos from, Goal goal, AStar.Ctx ctx) {
        return new Search(new AStar(from, goal, ctx));
    }
}
