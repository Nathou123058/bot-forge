package fr.nathvavan.nathbot.feature;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.Waypoints;
import fr.nathvavan.nathbot.client.Render;
import fr.nathvavan.nathbot.path.Path;
import fr.nathvavan.nathbot.task.MiscTasks;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

/** Registre de toutes les fonctions activables. */
public final class Features {
    private Features() {}

    private static final LinkedHashMap<String, Feature> ALL = new LinkedHashMap<>();
    private static boolean loaded = false;

    /** Fonctions qui font bouger le joueur toutes seules : jamais reactivees au lancement du jeu. */
    private static final Set<String> NOT_SAVED = Set.of("auto_walk", "anti_afk");
    /** Fonctions qui prennent le controle du joueur : coupees par l'arret d'urgence (K deux fois). */
    public static final List<String> TAKE_CONTROL = List.of("auto_walk", "anti_afk", "auto_sleep", "auto_fish", "kill_aura", "safe_walk", "auto_sprint");

    /**
     * Arret d'urgence des fonctions qui bougent le joueur.
     * @param all false = seulement marche auto / anti-AFK, true = toutes celles de TAKE_CONTROL
     * @return les noms des fonctions coupees
     */
    public static List<String> emergencyOff(boolean all) {
        List<String> off = new ArrayList<>();
        for (String id : all ? TAKE_CONTROL : List.copyOf(NOT_SAVED)) {
            Feature f = get(id);
            if (f != null && f.enabled) {
                set(id, false);
                off.add(f.name);
            }
        }
        return off;
    }

    public static synchronized Collection<Feature> all() {
        init();
        return ALL.values();
    }

    public static Feature get(String id) {
        init();
        return ALL.get(id);
    }

    public static boolean isOn(String id) {
        Feature f = get(id);
        return f != null && f.enabled;
    }

    public static void set(String id, boolean on) {
        Feature f = get(id);
        if (f == null || f.enabled == on) return;
        f.enabled = on;
        try {
            if (on) f.onEnable(); else f.onDisable();
        } catch (Throwable ignored) {}
        save();
    }

    public static boolean toggle(String id) {
        Feature f = get(id);
        if (f == null) return false;
        set(id, !f.enabled);
        return true;
    }

    // ------------------------------------------------------------------ persistance

    private static File file() {
        File d = new File(Minecraft.getInstance().gameDirectory, "nathbot");
        if (!d.exists()) d.mkdirs();
        return new File(d, "features.json");
    }

    private static void save() {
        Map<String, Boolean> m = new TreeMap<>();
        for (Feature f : ALL.values()) m.put(f.id, f.enabled && !NOT_SAVED.contains(f.id));
        try (Writer w = new FileWriter(file())) {
            new Gson().toJson(m, w);
        } catch (Exception ignored) {}
    }

    private static synchronized void init() {
        if (loaded) return;
        loaded = true;
        registerAll();
        File f = file();
        if (f.exists()) {
            try (Reader r = new FileReader(f)) {
                Map<String, Boolean> m = new Gson().fromJson(r, new TypeToken<Map<String, Boolean>>() {}.getType());
                if (m != null) m.forEach((k, v) -> { Feature ft = ALL.get(k); if (ft != null && !NOT_SAVED.contains(k)) ft.enabled = v; });
            } catch (Exception ignored) {}
        }
    }

    private static void add(Feature f) { ALL.put(f.id, f); }

    // ------------------------------------------------------------------ boucle

    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (Feature f : all()) {
            if (!f.enabled) continue;
            try {
                f.tick();
            } catch (Throwable t) {
                fr.nathvavan.nathbot.NathBot.LOG.warn("Erreur fonction {}", f.id, t);
            }
        }
    }

    public static void renderAll(Render.Ctx ctx) {
        for (Feature f : all()) {
            if (!f.enabled) continue;
            try {
                f.render(ctx);
            } catch (Throwable ignored) {}
        }
    }

    public static List<String> hudLines() {
        List<String> l = new ArrayList<>();
        for (Feature f : all()) {
            if (!f.enabled) continue;
            try {
                f.hud(l);
            } catch (Throwable ignored) {}
        }
        return l;
    }

    static void actionBar(String msg, ChatFormatting c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg).withStyle(c), true);
    }

    static boolean botBusy() { return Bot.I.isActive(); }

    static AABB around(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xOld, e.getX()) - e.getX();
        double y = Mth.lerp(pt, e.yOld, e.getY()) - e.getY();
        double z = Mth.lerp(pt, e.zOld, e.getZ()) - e.getZ();
        return e.getBoundingBox().move(x, y, z);
    }

    static Iterable<Entity> entities() {
        return Minecraft.getInstance().level.entitiesForRendering();
    }

    // ================================================================== definitions

    private static void registerAll() {
        // ---------------------------------------------------------- Survie
        add(new Feature("auto_eat", "Manger automatiquement", "Survie", true, "Mange quand la faim baisse (meilleure nourriture d'abord).") {
            int eating = 0;

            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                int food = p.getFoodData().getFoodLevel();
                int limit = BotConfig.get(BotConfig.EAT_BELOW_FOOD, 14);
                boolean need = food <= limit || (p.getHealth() < 12 && food < 20);
                if (eating > 0) {
                    eating++;
                    Bot.I.featureBusy = true;
                    Bot.I.requestUseKey();
                    if (!p.isUsingItem() && eating > 4 || eating > 60) eating = 0;
                    return;
                }
                if (!need || p.getAbilities().instabuild || mc.screen != null && !botBusy()) return;
                if (!botBusy() && mc.options.keyAttack.isDown()) return;
                int best = -1, val = -1;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = Inv.get(i);
                    if (!Inv.isGoodFood(s)) continue;
                    int v = Inv.foodValue(s);
                    if (v > val) { val = v; best = i; }
                }
                if (best < 0) return;
                final ItemStack target = Inv.get(best);
                if (!Inv.select(s -> s == target, 2)) return;
                mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                Bot.I.requestUseKey();
                Bot.I.featureBusy = true;
                eating = 1;
            }
        });
        add(new Feature("auto_tool", "Outil automatique", "Survie", true, "Prend le meilleur outil quand tu mines a la main.") {
            @Override
            public void tick() {
                if (botBusy() || !mc.options.keyAttack.isDown()) return;
                if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) Inv.selectBestTool(WV.st(bhr.getBlockPos()));
            }
        });
        add(new Feature("auto_weapon", "Arme automatique", "Combat", true, "Prend la meilleure arme quand tu vises une creature.") {
            @Override
            public void tick() {
                if (botBusy() || !mc.options.keyAttack.isDown()) return;
                if (mc.hitResult instanceof EntityHitResult) Inv.selectBestWeapon();
            }
        });
        add(new Feature("auto_armor", "Armure automatique", "Survie", true, "Equipe automatiquement la meilleure armure.") {
            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (p.tickCount % 20 != 0 || p.containerMenu != p.inventoryMenu || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack worn = p.getItemBySlot(slot);
                    float cur = score(worn, slot);
                    int best = -1;
                    float bs = cur;
                    for (int i = 0; i < 36; i++) {
                        float s = score(Inv.get(i), slot);
                        if (s > bs) { bs = s; best = i; }
                    }
                    if (best < 0) continue;
                    int armorMenuSlot = 8 - slot.getIndex();
                    if (!worn.isEmpty()) {
                        if (Inv.freeSlots() == 0) continue;
                        Inv.click(armorMenuSlot, 0, ClickType.QUICK_MOVE);
                    }
                    Inv.click(Inv.menuSlotFor(best), 0, ClickType.QUICK_MOVE);
                    return;
                }
            }

            float score(ItemStack s, EquipmentSlot slot) {
                if (s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot) return a.getDefense() * 10 + a.getToughness() + (s.isEnchanted() ? 1 : 0);
                if (slot == EquipmentSlot.CHEST && s.is(Items.ELYTRA)) return 0.5f;
                return 0;
            }
        });
        add(new Feature("auto_totem", "Totem automatique", "Survie", true, "Garde un totem d'immortalite dans la main gauche.") {
            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (p.tickCount % 5 != 0 || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || p.containerMenu != p.inventoryMenu) return;
                int i = Inv.find(s -> s.is(Items.TOTEM_OF_UNDYING));
                if (i >= 0) Inv.click(Inv.menuSlotFor(i), 40, ClickType.SWAP);
            }
        });
        add(new Feature("auto_respawn", "Reapparition auto", "Survie", true, "Reapparait directement apres la mort.") {
            @Override
            public void tick() {
                if (mc.screen instanceof DeathScreen && mc.player.isDeadOrDying()) {
                    mc.player.respawn();
                    mc.setScreen(null);
                }
            }
        });
        add(new Feature("death_waypoint", "Point de mort", "Survie", true, "Enregistre l'endroit de ta mort (/nb goto mort).") {
            boolean wasDead = false;

            @Override
            public void tick() {
                boolean dead = mc.player.isDeadOrDying();
                if (dead && !wasDead) {
                    BlockPos p = mc.player.blockPosition();
                    Waypoints.set("mort", p);
                    Bot.I.stats.deaths++;
                    BotLog.warn("Mort en " + p.toShortString() + " (point 'mort' enregistre).");
                }
                wasDead = dead;
            }
        });
        add(new Feature("auto_torch", "Torches automatiques", "Survie", false, "Pose des torches dans le noir pendant que le bot mine.") {
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0 || !botBusy() || !Inv.has(Items.TORCH)) return;
                BlockPos f = Bot.feet();
                if (mc.level.getBrightness(LightLayer.BLOCK, f) > 1 || mc.level.getBrightness(LightLayer.SKY, f) > 3) return;
                if (!WV.isAir(f) || !WV.solidSupport(f.below())) return;
                Actions.placeAgainst(f.below(), Direction.UP, s -> s.is(Items.TORCH));
                cd = 20;
            }
        });
        add(new Feature("mlg_water", "MLG seau d'eau", "Survie", true, "Pose de l'eau avant de toucher le sol lors d'une grosse chute.") {
            int state = 0;
            int timer = 0;

            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (p.getAbilities().flying || p.isFallFlying()) return;
                if (state == 0) {
                    if (p.fallDistance > 5 && p.getDeltaMovement().y < -0.5 && Inv.has(Items.WATER_BUCKET) && mc.level.dimension() != Level.NETHER) {
                        BlockPos below = null;
                        for (int i = 1; i < 6; i++) {
                            BlockPos q = p.blockPosition().below(i);
                            if (!WV.passable(q)) { below = q; break; }
                            if (WV.isWater(q)) return;
                        }
                        if (below == null) return;
                        Bot.I.featureBusy = true;
                        Inv.select(Items.WATER_BUCKET);
                        Rot.setRotation(p.getYRot(), 90);
                        if (p.getY() - (below.getY() + 1) < 3.2) {
                            Rot.syncNow();
                            mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                            state = 1;
                            timer = 0;
                        }
                    }
                } else {
                    Bot.I.featureBusy = true;
                    timer++;
                    if ((p.onGround() || p.isInWater()) && timer > 3 && Inv.has(Items.BUCKET)) {
                        Inv.select(Items.BUCKET);
                        Rot.setRotation(p.getYRot(), 90);
                        Rot.syncNow();
                        mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                        state = 0;
                    }
                    if (timer > 40) state = 0;
                }
            }
        });
        add(new Feature("durability_guard", "Proteger les outils", "Survie", true, "N'utilise plus un outil presque casse (5 de durabilite).") {
            int warnCd = 0;

            @Override
            public void tick() {
                ItemStack s = mc.player.getMainHandItem();
                if (warnCd > 0) warnCd--;
                if (s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 5 && warnCd == 0) {
                    actionBar("Attention : " + s.getHoverName().getString() + " presque casse !", ChatFormatting.RED);
                    warnCd = 100;
                }
            }
        });
        add(new Feature("auto_replant", "Replanter auto", "Ferme", true, "Replante quand tu recoltes une plante mure a la main.") {
            BlockPos pending;
            Item seed;
            int timeout;

            @Override
            public void tick() {
                if (botBusy()) return;
                if (mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                    BlockState s = WV.st(bhr.getBlockPos());
                    if (s.getBlock() instanceof CropBlock) {
                        pending = bhr.getBlockPos();
                        seed = MiscTasks.Farm.seedFor(s.getBlock());
                        timeout = 40;
                    }
                }
                if (pending != null && --timeout <= 0) pending = null;
                if (pending != null && WV.isAir(pending) && WV.st(pending.below()).is(Blocks.FARMLAND) && seed != null && Inv.has(seed)) {
                    int old = mc.player.getInventory().selected;
                    final Item sd = seed;
                    if (Inv.select(st -> st.is(sd), 2)) {
                        Actions.useOnBlock(pending.below(), Direction.UP);
                        mc.player.getInventory().selected = old;
                    }
                    pending = null;
                }
            }
        });
        add(new Feature("auto_sleep", "Dormir automatiquement", "Survie", false, "La nuit, va dormir dans un lit proche si le bot ne fait rien.") {
            @Override
            public void tick() {
                if (mc.player.tickCount % 100 != 0 || Bot.I.task() != null || mc.player.isSleeping()) return;
                long t = mc.level.getDayTime() % 24000;
                if (t < 12600 || t > 23400 || mc.level.dimension() != Level.OVERWORLD) return;
                if (Scanner.nearest(s -> s.getBlock() instanceof BedBlock, Bot.feet(), 1) == null) return;
                Bot.I.start(new MiscTasks.Sleep());
            }
        });
        add(new Feature("auto_drop_junk", "Jeter les dechets", "Survie", false, "Jette chair putrefiee, patates empoisonnees... quand l'inventaire est plein.") {
            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (p.tickCount % 20 != 0 || Inv.freeSlots() > 0 || p.containerMenu != p.inventoryMenu) return;
                Set<Item> junk = Set.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.WHEAT_SEEDS, Items.GRAVEL, Items.TUFF,
                        Items.CALCITE, Items.GRANITE, Items.DIORITE, Items.ANDESITE);
                for (int i = 9; i < 36; i++) {
                    if (junk.contains(Inv.get(i).getItem())) {
                        Inv.click(Inv.menuSlotFor(i), 1, ClickType.THROW);
                        return;
                    }
                }
            }
        });
        add(new Feature("smart_inventory", "Inventaire intelligent", "Bot", true,
                "Quand le bot travaille : regroupe les piles, jette le moins utile si c'est plein, range la barre rapide (epee, pioche, hache, pelle, blocs, nourriture).") {
            @Override
            public void tick() { fr.nathvavan.nathbot.util.InvManager.tick(); }
        });
        add(new Feature("show_inventory", "Voir le bot ranger", "Bot", true, "Ouvre vraiment l'inventaire a l'ecran pendant que le bot range.") {});
        add(new Feature("anti_drown", "Ne pas se noyer", "Survie", true, "Quand le bot travaille sous l'eau, il remonte respirer avant de manquer d'air.") {
            private boolean up = false;

            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (Bot.I.task() == null || Bot.I.paused || p.isPassenger()) { up = false; return; }
                if (p.isUnderWater() && p.getAirSupply() < 110 && !up) {
                    up = true;
                    fr.nathvavan.nathbot.util.Brain.think("eau", "Plus beaucoup d'air : je remonte respirer.");
                }
                if (up && (p.getAirSupply() >= p.getMaxAirSupply() - 10 || !p.isInWater())) up = false;
                if (up) {
                    Bot.I.in.active = true;
                    Bot.I.in.jump = true;
                }
            }
        });
        add(new Feature("survival_reflex", "Reflexe de survie", "Survie", true,
                "Vie basse pendant une tache : au lieu de s'arreter, le bot creuse un trou, se couvre, mange et attend d'etre soigne.") {});
        add(new Feature("auto_boat", "Bateau automatique", "Deplacement", true, "Pour traverser beaucoup d'eau, le bot fabrique un bateau, navigue puis le recupere.") {});
        add(new Feature("no_pause", "Pas de pause hors focus", "Bot", true, "Le jeu ne se met pas en pause quand tu vas sur le site web.") {
            @Override
            public void tick() { mc.options.pauseOnLostFocus = false; }

            @Override
            public void onDisable() { mc.options.pauseOnLostFocus = true; }
        });
        add(new Feature("chat_feedback", "Messages du bot", "Bot", true, "Le bot raconte ce qu'il fait dans le chat.") {});

        // ---------------------------------------------------------- Combat
        add(new Feature("kill_aura", "Defense automatique", "Combat", false, "Attaque les monstres hostiles a portee (pas les joueurs).") {
            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (!Actions.canAttack() || mc.screen != null && !botBusy()) return;
                LivingEntity best = null;
                double bd = 3.3 * 3.3;
                for (Entity e : entities()) {
                    if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                    if (e instanceof net.minecraft.world.entity.monster.EnderMan em && !em.isCreepy()) continue;
                    double d = e.distanceToSqr(p.getEyePosition());
                    if (d < bd) { bd = d; best = le; }
                }
                if (best == null) return;
                if (botBusy()) Inv.selectBestWeapon();
                Actions.attack(best);
            }
        });
        add(new Feature("hostile_alert", "Alerte creeper", "Combat", true, "Previent quand un creeper est tout pres.") {
            int cd = 0;

            @Override
            public void tick() {
                if (cd > 0) { cd--; return; }
                for (Entity e : entities()) {
                    if (e instanceof Creeper && e.distanceTo(mc.player) < 7) {
                        actionBar("ATTENTION : creeper a " + (int) e.distanceTo(mc.player) + " blocs !", ChatFormatting.RED);
                        cd = 40;
                        return;
                    }
                }
            }
        });
        add(new Feature("low_health_alert", "Alerte vie basse", "Combat", true, "Message quand la vie est basse.") {
            int cd = 0;

            @Override
            public void tick() {
                if (cd > 0) { cd--; return; }
                if (mc.player.getHealth() <= 6 && mc.player.isAlive()) {
                    actionBar("Vie basse : " + (int) mc.player.getHealth() + " PV !", ChatFormatting.DARK_RED);
                    cd = 60;
                }
            }
        });

        // ---------------------------------------------------------- Deplacement
        add(new Feature("auto_sprint", "Sprint automatique", "Deplacement", false, "Sprinte des que tu avances.") {
            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (!botBusy() && mc.options.keyUp.isDown() && p.getFoodData().getFoodLevel() > 6 && !p.isShiftKeyDown()) p.setSprinting(true);
            }
        });
        add(new Feature("safe_walk", "Marche securisee", "Deplacement", false, "S'accroupit au bord du vide pour ne pas tomber.") {});
        add(new Feature("auto_walk", "Marche automatique", "Deplacement", false, "Avance tout seul. S (reculer) ou K pour arreter. Jamais reactivee au lancement.") {
            @Override
            public void onEnable() { BotLog.info("Marche automatique : appuie sur S (reculer) ou K pour arreter."); }
        });
        add(new Feature("anti_afk", "Anti-AFK", "Deplacement", false, "Bouge un peu toutes les minutes pour ne pas etre expulse.") {
            @Override
            public void tick() {
                if (botBusy() || mc.player.tickCount % 1200 != 0) return;
                mc.player.setYRot(mc.player.getYRot() + 90);
                if (mc.player.onGround()) mc.player.jumpFromGround();
            }
        });
        add(new Feature("auto_fish", "Peche automatique", "Ferme", false, "Tiens une canne a peche : le bot lance et ferre tout seul.") {
            int cd = 0;
            double lastY = 0;
            int stable = 0;

            @Override
            public void tick() {
                LocalPlayer p = mc.player;
                if (!(p.getMainHandItem().getItem() instanceof FishingRodItem)) return;
                if (cd > 0) { cd--; return; }
                FishingHook hook = p.fishing;
                if (hook == null) {
                    mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                    cd = 20;
                    stable = 0;
                    return;
                }
                double vy = hook.getDeltaMovement().y;
                if (hook.isInWater()) {
                    if (Math.abs(vy) < 0.03) stable++;
                    if (stable > 20 && vy < -0.08 && hook.getY() < lastY) {
                        mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                        cd = 15;
                        stable = 0;
                    }
                }
                lastY = hook.getY();
            }
        });

        // ---------------------------------------------------------- Rendu
        add(new Feature("fullbright", "Vision nocturne", "Rendu", false, "Voir clair dans le noir (effet local).") {
            @Override
            public void tick() {
                MobEffectInstance e = mc.player.getEffect(MobEffects.NIGHT_VISION);
                if (e == null || e.getDuration() < 400) mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false));
            }

            @Override
            public void onDisable() {
                if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
            }
        });
        add(new Feature("ore_esp", "Detecteur de minerais", "Rendu", false, "Encadre les minerais precieux a travers les murs.") {
            List<BlockPos> cache = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0) return;
                cd = 40;
                List<BlockPos> found = Scanner.find(Features::isValuableOre, Bot.feet(), 4, 400, false);
                List<Integer> c = new ArrayList<>();
                for (BlockPos p : found) c.add(oreColor(WV.st(p)));
                cache = found;
                colors = c;
            }

            @Override
            public void render(Render.Ctx ctx) {
                for (int i = 0; i < cache.size(); i++) ctx.box(new AABB(cache.get(i)).deflate(0.05), colors.get(i));
            }
        });
        add(new Feature("mob_esp", "Voir les monstres", "Rendu", false, "Encadre les monstres hostiles en rouge.") {
            @Override
            public void render(Render.Ctx ctx) {
                for (Entity e : entities()) if (e instanceof Enemy && e.isAlive()) ctx.box(around(e, ctx.partialTick), 0xFFFF3030);
            }
        });
        add(new Feature("animal_esp", "Voir les animaux", "Rendu", false, "Encadre les animaux en vert.") {
            @Override
            public void render(Render.Ctx ctx) {
                for (Entity e : entities()) if (e instanceof net.minecraft.world.entity.animal.Animal && e.isAlive()) ctx.box(around(e, ctx.partialTick), 0xFF30FF60);
            }
        });
        add(new Feature("player_esp", "Voir les joueurs", "Rendu", false, "Encadre les autres joueurs en bleu.") {
            @Override
            public void render(Render.Ctx ctx) {
                for (Entity e : entities()) if (e instanceof Player && e != mc.player) ctx.box(around(e, ctx.partialTick), 0xFF3080FF);
            }
        });
        add(new Feature("item_esp", "Voir les objets", "Rendu", false, "Encadre les objets au sol en jaune.") {
            @Override
            public void render(Render.Ctx ctx) {
                for (Entity e : entities()) if (e instanceof ItemEntity) ctx.box(around(e, ctx.partialTick), 0xFFFFE030);
            }
        });
        add(new Feature("chest_esp", "Voir les coffres", "Rendu", false, "Encadre coffres, tonneaux et boites de Shulker.") {
            List<BlockPos> cache = new ArrayList<>();
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0) return;
                cd = 40;
                cache = blockEntities(be -> be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity
                        || be instanceof EnderChestBlockEntity);
            }

            @Override
            public void render(Render.Ctx ctx) {
                for (BlockPos p : cache) ctx.box(new AABB(p).deflate(0.06), 0xFFFF9020);
            }
        });
        add(new Feature("special_esp", "Spawners et portails", "Rendu", false, "Encadre spawners, portails et cadres de l'End.") {
            List<BlockPos> cache = new ArrayList<>();
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0) return;
                cd = 60;
                cache = Scanner.find(s -> s.is(Blocks.SPAWNER) || s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.NETHER_PORTAL)
                        || s.is(Blocks.END_PORTAL), Bot.feet(), 6, 200, false);
            }

            @Override
            public void render(Render.Ctx ctx) {
                for (BlockPos p : cache) ctx.box(new AABB(p).deflate(0.04), 0xFFC040FF);
            }
        });
        add(new Feature("crop_esp", "Plantes mures", "Rendu", false, "Encadre les plantes pretes a etre recoltees.") {
            List<BlockPos> cache = new ArrayList<>();
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0) return;
                cd = 40;
                cache = Scanner.find(MiscTasks.Farm::mature, Bot.feet(), 2, 300, false);
            }

            @Override
            public void render(Render.Ctx ctx) {
                for (BlockPos p : cache) ctx.box(new AABB(p).deflate(0.2), 0xFF80FF40);
            }
        });
        add(new Feature("light_overlay", "Zones de spawn", "Rendu", false, "Montre les blocs sombres ou les monstres peuvent apparaitre.") {
            List<BlockPos> cache = new ArrayList<>();
            int cd = 0;

            @Override
            public void tick() {
                if (cd-- > 0) return;
                cd = 20;
                List<BlockPos> l = new ArrayList<>();
                BlockPos f = Bot.feet();
                for (int x = -10; x <= 10; x++)
                    for (int z = -10; z <= 10; z++)
                        for (int y = -4; y <= 4; y++) {
                            BlockPos p = f.offset(x, y, z);
                            if (!WV.isAir(p) || !WV.isAir(p.above())) continue;
                            if (!WV.st(p.below()).isFaceSturdy(mc.level, p.below(), Direction.UP)) continue;
                            if (mc.level.getBrightness(LightLayer.BLOCK, p) == 0) l.add(p);
                        }
                cache = l;
            }

            @Override
            public void render(Render.Ctx ctx) {
                for (BlockPos p : cache) ctx.box(new AABB(p.getX() + 0.1, p.getY(), p.getZ() + 0.1, p.getX() + 0.9, p.getY() + 0.02, p.getZ() + 0.9), 0xFFFF2020);
            }
        });
        add(new Feature("tracers", "Lignes vers les joueurs", "Rendu", false, "Trace une ligne vers les joueurs et monstres proches.") {
            @Override
            public void render(Render.Ctx ctx) {
                Vec3 a = ctx.eyeAnchor();
                for (Entity e : entities()) {
                    if (e == mc.player) continue;
                    if (e instanceof Player) ctx.line(a, around(e, ctx.partialTick).getCenter(), 0xFF3080FF);
                    else if (e instanceof Enemy && e.distanceTo(mc.player) < 48) ctx.line(a, around(e, ctx.partialTick).getCenter(), 0x90FF3030);
                }
            }
        });
        add(new Feature("path_render", "Afficher le chemin", "Rendu", true, "Dessine le chemin calcule par le bot.") {
            @Override
            public void render(Render.Ctx ctx) {
                Path p = Bot.I.renderPath;
                if (p == null) return;
                int start = Math.max(0, Bot.I.renderIdx - 1);
                for (int i = start; i < p.steps.size(); i++) {
                    Path.Step s = p.steps.get(i);
                    Vec3 a = Vec3.atBottomCenterOf(s.from()).add(0, 0.1, 0);
                    Vec3 b = Vec3.atBottomCenterOf(s.to()).add(0, 0.1, 0);
                    int c = s.placeFloor() || s.move() == Path.Move.PILLAR ? 0xFFFFB000 : 0xFF40FF80;
                    ctx.line(a, b, c);
                }
                BlockPos end = p.end();
                if (end != null) ctx.box(new AABB(end), p.partial ? 0xFFFFFF40 : 0xFF40FF40);
            }
        });
        add(new Feature("breadcrumbs", "Trace du trajet", "Rendu", false, "Garde une trace des endroits parcourus.") {
            final Deque<Vec3> trail = new ArrayDeque<>();

            @Override
            public void tick() {
                Vec3 p = mc.player.position();
                if (trail.isEmpty() || trail.peekLast().distanceToSqr(p) > 1) trail.addLast(p.add(0, 0.05, 0));
                while (trail.size() > 600) trail.removeFirst();
            }

            @Override
            public void render(Render.Ctx ctx) {
                Vec3 prev = null;
                for (Vec3 v : trail) {
                    if (prev != null && prev.distanceToSqr(v) < 16) ctx.line(prev, v, 0xFF20D0FF);
                    prev = v;
                }
            }
        });
        add(new Feature("zoom", "Zoom (touche C)", "Rendu", true, "Maintiens la touche de zoom pour zoomer x4.") {});

        // ---------------------------------------------------------- HUD
        add(new Feature("hud_status", "HUD : etat du bot", "HUD", true, "Affiche ce que fait le bot.") {
            @Override
            public void hud(List<String> l) {
                l.add("§bNathBot§7 : §f" + Bot.I.statusText());
            }
        });
        add(new Feature("hud_coords", "HUD : coordonnees", "HUD", true, "Coordonnees et direction.") {
            @Override
            public void hud(List<String> l) {
                LocalPlayer p = mc.player;
                l.add(String.format("§7XYZ §f%.1f %.1f %.1f §7(%s)", p.getX(), p.getY(), p.getZ(), dirName(p.getDirection())));
            }
        });
        add(new Feature("hud_nether", "HUD : coords Nether", "HUD", true, "Coordonnees converties Nether / monde normal.") {
            @Override
            public void hud(List<String> l) {
                LocalPlayer p = mc.player;
                if (mc.level.dimension() == Level.NETHER) l.add(String.format("§7Monde normal : §f%d %d", (int) (p.getX() * 8), (int) (p.getZ() * 8)));
                else if (mc.level.dimension() == Level.OVERWORLD) l.add(String.format("§7Nether : §f%d %d", (int) (p.getX() / 8), (int) (p.getZ() / 8)));
            }
        });
        add(new Feature("hud_info", "HUD : infos", "HUD", false, "FPS, heure du jeu, biome, lumiere.") {
            @Override
            public void hud(List<String> l) {
                long t = (mc.level.getDayTime() + 6000) % 24000;
                String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                int light = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition());
                l.add(String.format("§7FPS §f%d §7Heure §f%02d:%02d §7Biome §f%s §7Lumiere §f%d", mc.getFps(), t / 1000, (t % 1000) * 60 / 1000, biome, light));
            }
        });
        add(new Feature("hud_armor", "HUD : durabilite", "HUD", false, "Durabilite de l'outil en main et de l'armure.") {
            @Override
            public void hud(List<String> l) {
                StringBuilder sb = new StringBuilder("§7Durabilite ");
                ItemStack h = mc.player.getMainHandItem();
                if (h.isDamageableItem()) sb.append("§fmain ").append(h.getMaxDamage() - h.getDamageValue()).append(' ');
                for (ItemStack a : mc.player.getArmorSlots()) if (a.isDamageableItem()) sb.append("§f").append(a.getMaxDamage() - a.getDamageValue()).append(' ');
                l.add(sb.toString());
            }
        });
        add(new Feature("hud_compass", "HUD : boussole", "HUD", true, "Direction et distance vers le point actif (/nb wp suivre).") {
            @Override
            public void hud(List<String> l) {
                String a = Waypoints.active;
                if (a == null) return;
                Waypoints.Wp w = Waypoints.get(a);
                if (w == null) return;
                LocalPlayer p = mc.player;
                double dx = w.x + 0.5 - p.getX(), dz = w.z + 0.5 - p.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
                float rel = Mth.wrapDegrees(yaw - p.getYRot());
                String arrow = rel > -22.5 && rel <= 22.5 ? "↑" : rel > 22.5 && rel <= 67.5 ? "↗" : rel > 67.5 && rel <= 112.5 ? "→"
                        : rel > 112.5 && rel <= 157.5 ? "↘" : rel > -67.5 && rel <= -22.5 ? "↖" : rel > -112.5 && rel <= -67.5 ? "←"
                        : rel > -157.5 && rel <= -112.5 ? "↙" : "↓";
                l.add(String.format("§e%s §f%s §7%d blocs (Y %d)", arrow, w.name, (int) dist, w.y));
            }
        });
        add(new Feature("hud_stats", "HUD : statistiques", "HUD", false, "Blocs mines, distance, temps de jeu.") {
            @Override
            public void hud(List<String> l) {
                var s = Bot.I.stats;
                long sec = s.seconds();
                l.add(String.format("§7Mines §f%d §7Distance §f%dm §7Kills §f%d §7Temps §f%d:%02d", s.blocksMined, (int) s.distance, s.kills, sec / 60, sec % 60));
            }
        });
        add(new Feature("hud_items", "HUD : tresors", "HUD", false, "Compte les objets precieux de l'inventaire.") {
            @Override
            public void hud(List<String> l) {
                l.add(String.format("§bDiamants %d §fFer %d §6Or %d §aEmeraudes %d §8Netherite %d",
                        Inv.count(Items.DIAMOND), Inv.count(Items.IRON_INGOT) + Inv.count(Items.RAW_IRON), Inv.count(Items.GOLD_INGOT) + Inv.count(Items.RAW_GOLD),
                        Inv.count(Items.EMERALD), Inv.count(Items.NETHERITE_INGOT) + Inv.count(Items.ANCIENT_DEBRIS)));
            }
        });
        add(new Feature("speedometer", "HUD : vitesse", "HUD", false, "Vitesse de deplacement en blocs par seconde.") {
            @Override
            public void hud(List<String> l) {
                l.add(String.format("§7Vitesse §f%.1f b/s", Bot.I.stats.speed));
            }
        });
        add(new Feature("inventory_alert", "Alerte inventaire plein", "HUD", true, "Previent quand l'inventaire est plein.") {
            int cd = 0;

            @Override
            public void tick() {
                if (cd > 0) { cd--; return; }
                if (Inv.freeSlots() == 0) {
                    actionBar("Inventaire plein !", ChatFormatting.GOLD);
                    cd = 200;
                }
            }
        });
        add(new Feature("night_alert", "Alerte nuit", "HUD", false, "Previent quand la nuit tombe.") {
            boolean wasNight = false;

            @Override
            public void tick() {
                long t = mc.level.getDayTime() % 24000;
                boolean night = t > 12500 && t < 23500;
                if (night && !wasNight && mc.level.dimension() == Level.OVERWORLD) actionBar("La nuit tombe : attention aux monstres !", ChatFormatting.DARK_PURPLE);
                wasNight = night;
            }
        });
    }

    static String dirName(Direction d) {
        return switch (d) {
            case NORTH -> "Nord";
            case SOUTH -> "Sud";
            case EAST -> "Est";
            case WEST -> "Ouest";
            default -> d.getName();
        };
    }

    public static boolean isValuableOre(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE || b == Blocks.ANCIENT_DEBRIS || b == Blocks.EMERALD_ORE
                || b == Blocks.DEEPSLATE_EMERALD_ORE || b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.IRON_ORE
                || b == Blocks.DEEPSLATE_IRON_ORE || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE || b == Blocks.REDSTONE_ORE
                || b == Blocks.DEEPSLATE_REDSTONE_ORE;
    }

    static int oreColor(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return 0xFF30F0FF;
        if (b == Blocks.ANCIENT_DEBRIS) return 0xFF8B5A2B;
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return 0xFF20FF40;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE) return 0xFFFFD020;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) return 0xFFE0B090;
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) return 0xFF2040FF;
        return 0xFFFF2020;
    }

    static List<BlockPos> blockEntities(java.util.function.Predicate<BlockEntity> pred) {
        List<BlockPos> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        BlockPos f = mc.player.blockPosition();
        int r = 6;
        for (int cx = (f.getX() >> 4) - r; cx <= (f.getX() >> 4) + r; cx++)
            for (int cz = (f.getZ() >> 4) - r; cz <= (f.getZ() >> 4) + r; cz++) {
                LevelChunk ch = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (pred.test(be)) out.add(be.getBlockPos());
            }
        return out;
    }
}
