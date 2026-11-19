package fr.nathvavan.nathbot;

import com.mojang.logging.LogUtils;
import fr.nathvavan.nathbot.client.ClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * NathBot : bot autonome 100 % maison pour Minecraft Forge 1.20.1.
 * Aucune dependance a Baritone : pathfinding A*, minage, construction, etc. sont ecrits ici.
 */
@Mod(NathBot.MODID)
public class NathBot {
    public static final String MODID = "nathbot";
    public static final String VERSION = "1.0.0";
    public static final Logger LOG = LogUtils.getLogger();

    public NathBot() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BotConfig.SPEC, "nathbot-client.toml");
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::init);
    }
}
