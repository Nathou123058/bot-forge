package fr.nathvavan.nathbot.bot;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.TreeMap;

/** Statistiques de la session (affichees dans le HUD et sur le site web). */
public final class Stats {
    public final long startMillis = System.currentTimeMillis();
    public long blocksMined;
    public double distance;
    public int tasksStarted;
    public int tasksDone;
    public int kills;
    public int deaths;
    public double speed; // blocs / seconde
    public final Map<String, Integer> minedByType = new TreeMap<>();
    private Vec3 last;

    void tick(LocalPlayer p) {
        Vec3 pos = p.position();
        if (last != null) {
            double d = pos.distanceTo(last);
            if (d < 10) {
                distance += d;
                speed = speed * 0.8 + d * 20 * 0.2;
            }
        }
        last = pos;
    }

    public synchronized void blockMined(BlockState s) {
        blocksMined++;
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        minedByType.merge(id, 1, Integer::sum);
    }

    public synchronized Map<String, Integer> minedCopy() {
        return new TreeMap<>(minedByType);
    }

    public long seconds() {
        return (System.currentTimeMillis() - startMillis) / 1000;
    }
}
