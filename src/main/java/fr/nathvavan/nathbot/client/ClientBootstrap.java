package fr.nathvavan.nathbot.client;

import fr.nathvavan.nathbot.build.SchematicIO;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.gui.SettingsScreen;
import fr.nathvavan.nathbot.web.WebServer;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Initialisation cote client. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientBootstrap::onKeys);
        modBus.addListener(ClientBootstrap::onOverlays);
        modBus.addListener(ClientBootstrap::onSetup);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }

    private static void onKeys(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : Keys.ALL) e.register(k);
    }

    private static void onOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("nathbot_hud", Hud::render);
    }

    private static void onSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            Features.all();
            try {
                SchematicIO.reload();
            } catch (Throwable ignored) {}
            WebServer.start();
        });
    }
}
