package fr.nathvavan.nathbot.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.BotInput;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.gui.PanelScreen;
import fr.nathvavan.nathbot.task.FollowTask;
import fr.nathvavan.nathbot.task.GatherTask;
import fr.nathvavan.nathbot.task.SpeedrunTask;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.WV;
import fr.nathvavan.nathbot.web.WebServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Evenements Forge cote client. */
public final class ClientEvents {
    private ClientEvents() {}

    private static boolean welcomed = false;
    private static boolean socialKeyFixed = false;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!WebServer.running() && BotConfig.get(BotConfig.WEB_ENABLED, true) && mc.player != null && mc.player.tickCount == 40) WebServer.start();
        fixSocialKey(mc);
        if (mc.player != null && !welcomed && mc.player.tickCount > 60) {
            welcomed = true;
            BotLog.info("NathBot pret ! Touche " + Keys.PANEL.getTranslatedKeyMessage().getString() + " = panneau, '/nb aide' = commandes, site : " + WebServer.url());
        }
        handleKeys(mc);
        fr.nathvavan.nathbot.util.DropIndex.tick();
        Bot.I.tick();
    }

    /** Le serveur a (re)envoye ses recettes : on reconstruira l'index. */
    @SubscribeEvent
    public static void onRecipes(RecipesUpdatedEvent e) {
        fr.nathvavan.nathbot.util.RecipeBook.invalidate();
    }

    /** La touche P sert aussi a "Interactions sociales" en vanilla : on libere P pour le panneau. */
    private static void fixSocialKey(Minecraft mc) {
        if (socialKeyFixed || mc.options == null) return;
        socialKeyFixed = true;
        if (mc.options.keySocialInteractions.same(Keys.PANEL)) {
            mc.options.keySocialInteractions.setKey(InputConstants.UNKNOWN);
            net.minecraft.client.KeyMapping.resetMapping();
            mc.options.save();
        }
    }

    private static void handleKeys(Minecraft mc) {
        if (mc.player == null) return;
        while (Keys.PANEL.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new PanelScreen());
            if (BotConfig.get(BotConfig.WEB_OPEN_FROM_PANEL, false)) WebServer.openInBrowser();
        }
        while (Keys.STOP.consumeClick()) Bot.I.stop();
        while (Keys.PAUSE.consumeClick()) {
            Bot.I.paused = !Bot.I.paused;
            BotLog.info(Bot.I.paused ? "Pause." : "Reprise.");
        }
        while (Keys.WEB.consumeClick()) WebServer.openInBrowser();
        while (Keys.DIAMONDS.consumeClick()) Bot.I.start(new GatherTask(Items.DIAMOND, Inv.count(Items.DIAMOND) + 5));
        while (Keys.FINISH.consumeClick()) Bot.I.start(new SpeedrunTask());
        while (Keys.ORE_ESP.consumeClick()) Features.toggle("ore_esp");
        while (Keys.FULLBRIGHT.consumeClick()) Features.toggle("fullbright");
        while (Keys.COME.consumeClick()) Bot.I.start(new FollowTask(null, true));
    }

    /** Applique les deplacements voulus par le bot (ou par les fonctions marche auto / securisee). */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player) return;
        Input in = e.getInput();
        BotInput b = Bot.I.in;
        if (b.active && (Bot.I.task() != null && !Bot.I.paused || Bot.I.featureBusy)) {
            in.forwardImpulse = b.forward;
            in.leftImpulse = b.strafe;
            in.up = b.forward > 0;
            in.down = b.forward < 0;
            in.left = b.strafe > 0;
            in.right = b.strafe < 0;
            in.jumping = b.jump;
            in.shiftKeyDown = b.sneak;
            if (b.sneak) {
                in.forwardImpulse *= 0.3f;
                in.leftImpulse *= 0.3f;
            }
            return;
        }
        if (Bot.I.task() != null && !Bot.I.paused) {
            // le bot reflechit : on ne bouge pas
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            return;
        }
        if (Features.isOn("auto_walk") && mc.screen == null) {
            in.forwardImpulse = 1;
            in.up = true;
        }
        if (Features.isOn("safe_walk") && mc.player.onGround() && !in.jumping && (in.forwardImpulse != 0 || in.leftImpulse != 0)) {
            LocalPlayer p = mc.player;
            Vec3 move = new Vec3(in.leftImpulse, 0, in.forwardImpulse).yRot(-p.getYRot() * (float) Math.PI / 180f).normalize().scale(0.45);
            BlockPos ahead = BlockPos.containing(p.getX() + move.x, p.getY() - 0.5, p.getZ() + move.z);
            if (WV.passable(ahead) && WV.passable(ahead.below())) {
                in.shiftKeyDown = true;
                in.forwardImpulse *= 0.3f;
                in.leftImpulse *= 0.3f;
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Render.Ctx ctx = Render.newCtx(e.getPartialTick(), e.getCamera().getPosition());
        Features.renderAll(ctx);
        Render.draw(e.getPoseStack(), ctx);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (Features.isOn("zoom") && Keys.ZOOM.isDown() && Minecraft.getInstance().screen == null) e.setFOV(e.getFOV() / 4.0);
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("nb")
                .executes(c -> {
                    Bot.I.commandQueue.add("aide");
                    return 1;
                })
                .then(net.minecraft.commands.Commands.argument("commande", StringArgumentType.greedyString())
                        .executes(c -> {
                            Bot.I.commandQueue.add(StringArgumentType.getString(c, "commande"));
                            return 1;
                        })));
    }

    /** Les messages qui commencent par # sont des commandes du bot (comme Baritone). */
    @SubscribeEvent
    public static void onChat(ClientChatEvent e) {
        String m = e.getMessage();
        if (m.startsWith("#") && m.length() > 1) {
            e.setCanceled(true);
            Minecraft.getInstance().gui.getChat().addRecentChat(m);
            Commands.execute(m.substring(1));
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        Bot.I.stop();
    }
}
