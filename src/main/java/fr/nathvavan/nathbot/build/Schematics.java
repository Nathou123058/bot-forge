package fr.nathvavan.nathbot.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.*;

/**
 * Bibliotheque de constructions predefinies, generees par code.
 * Origine (0,0,0) = coin avant-gauche au niveau du sol ; la construction s'etend vers +X, +Y, +Z.
 */
public final class Schematics {
    private Schematics() {}

    private static final LinkedHashMap<String, Schematic> BUILTIN = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Schematic> USER = new LinkedHashMap<>();

    public static synchronized Map<String, Schematic> all() {
        if (BUILTIN.isEmpty()) registerAll();
        LinkedHashMap<String, Schematic> m = new LinkedHashMap<>(BUILTIN);
        m.putAll(USER);
        return m;
    }

    public static Schematic get(String id) {
        return all().get(id.toLowerCase(Locale.ROOT));
    }

    public static synchronized void putUser(Schematic s) {
        USER.put(s.id, s);
    }

    public static synchronized void clearUser() {
        USER.clear();
    }

    // ------------------------------------------------------------------ outil de construction

    static final class B {
        final LinkedHashMap<BlockPos, BlockState> m = new LinkedHashMap<>();

        B set(int x, int y, int z, BlockState s) { m.put(new BlockPos(x, y, z), s); return this; }

        B set(int x, int y, int z, Block b) { return set(x, y, z, b.defaultBlockState()); }

        B fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
            return this;
        }

        B fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) { return fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState()); }

        /** Murs d'une boite (sans sol ni plafond). */
        B walls(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
            fill(x1, y1, z1, x2, y2, z1, b);
            fill(x1, y1, z2, x2, y2, z2, b);
            fill(x1, y1, z1, x1, y2, z2, b);
            fill(x2, y1, z1, x2, y2, z2, b);
            return this;
        }

        B air(int x1, int y1, int z1, int x2, int y2, int z2) { return fill(x1, y1, z1, x2, y2, z2, Blocks.AIR); }

        B door(int x, int y, int z, Block door, Direction facing) {
            BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing);
            set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            return this;
        }

        B stairs(int x, int y, int z, Block stairs, Direction facing) {
            return set(x, y, z, stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
        }

        B cylinder(int cx, int cz, double r, int y1, int y2, Block b, boolean hollow) {
            for (int y = y1; y <= y2; y++)
                for (int x = (int) -Math.ceil(r); x <= Math.ceil(r); x++)
                    for (int z = (int) -Math.ceil(r); z <= Math.ceil(r); z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d <= r + 0.5 && (!hollow || d >= r - 0.5)) set(cx + x, y, cz + z, b);
                    }
            return this;
        }

        B sphere(int cx, int cy, int cz, double r, Block b, boolean hollow, boolean topHalfOnly) {
            int ri = (int) Math.ceil(r);
            for (int y = topHalfOnly ? 0 : -ri; y <= ri; y++)
                for (int x = -ri; x <= ri; x++)
                    for (int z = -ri; z <= ri; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d <= r + 0.5 && (!hollow || d >= r - 0.5)) set(cx + x, cy + y, cz + z, b);
                    }
            return this;
        }

        Map<BlockPos, BlockState> done() { return m; }
    }

    private static void reg(String id, String name, String cat, String desc, B b) {
        BUILTIN.put(id, new Schematic(id, name, desc, cat, b.done()));
    }

    private static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    // ------------------------------------------------------------------ constructions

    private static void registerAll() {
        // 1. Abri de survie (premiere nuit)
        {
            B b = new B();
            b.fill(0, 0, 0, 4, 0, 4, Blocks.COBBLESTONE);
            b.walls(0, 1, 0, 4, 3, 4, Blocks.DIRT);
            b.fill(0, 4, 0, 4, 4, 4, Blocks.DIRT);
            b.air(1, 1, 1, 3, 3, 3);
            b.door(2, 1, 0, Blocks.OAK_DOOR, Direction.NORTH);
            b.set(2, 3, 3, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            b.set(1, 1, 3, Blocks.CRAFTING_TABLE);
            b.set(3, 1, 3, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
            reg("abri_survie", "Abri de survie", "Survie", "Petit abri en terre pour passer la premiere nuit (5x5).", b);
        }
        // 2. Cabane en bois
        {
            B b = new B();
            b.fill(0, 0, 0, 5, 0, 5, Blocks.OAK_PLANKS);
            b.walls(0, 1, 0, 5, 3, 5, Blocks.OAK_PLANKS);
            for (int[] c : new int[][]{{0, 0}, {5, 0}, {0, 5}, {5, 5}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], Blocks.OAK_LOG);
            b.air(1, 1, 1, 4, 3, 4);
            b.door(2, 1, 0, Blocks.OAK_DOOR, Direction.NORTH);
            b.set(0, 2, 2, Blocks.GLASS_PANE).set(5, 2, 3, Blocks.GLASS_PANE).set(3, 2, 5, Blocks.GLASS_PANE);
            BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
            b.fill(-1, 4, -1, 6, 4, 6, slab);
            b.fill(0, 4, 0, 5, 4, 5, Blocks.OAK_PLANKS);
            b.set(4, 1, 4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
            b.set(1, 1, 4, Blocks.CRAFTING_TABLE);
            reg("cabane", "Cabane en bois", "Maisons", "Cabane 6x6 avec porte, fenetres, coffre et etabli.", b);
        }
        // 3. Maison en bois avec toit en escalier
        {
            B b = new B();
            int w = 8, d = 10;
            b.fill(0, 0, 0, w, 0, d, Blocks.COBBLESTONE);
            b.walls(0, 1, 0, w, 4, d, Blocks.OAK_PLANKS);
            for (int[] c : new int[][]{{0, 0}, {w, 0}, {0, d}, {w, d}}) b.fill(c[0], 1, c[1], c[0], 4, c[1], Blocks.STRIPPED_OAK_LOG);
            b.air(1, 1, 1, w - 1, 4, d - 1);
            b.fill(1, 0, 1, w - 1, 0, d - 1, Blocks.SPRUCE_PLANKS);
            b.door(4, 1, 0, Blocks.OAK_DOOR, Direction.NORTH);
            for (int z = 2; z <= d - 2; z += 3) { b.set(0, 2, z, Blocks.GLASS_PANE).set(0, 3, z, Blocks.GLASS_PANE); b.set(w, 2, z, Blocks.GLASS_PANE).set(w, 3, z, Blocks.GLASS_PANE); }
            b.set(2, 2, 0, Blocks.GLASS_PANE).set(6, 2, 0, Blocks.GLASS_PANE);
            // toit
            for (int i = 0; i <= w / 2; i++) {
                int y = 5 + i;
                for (int z = -1; z <= d + 1; z++) {
                    b.stairs(i - 1, y, z, Blocks.SPRUCE_STAIRS, Direction.EAST);
                    b.stairs(w + 1 - i, y, z, Blocks.SPRUCE_STAIRS, Direction.WEST);
                }
                if (i > 0) for (int x = i; x <= w - i; x++) { b.set(x, y - 1, 0, Blocks.OAK_PLANKS); b.set(x, y - 1, d, Blocks.OAK_PLANKS); }
            }
            b.fill(w / 2, 5 + w / 2, -1, w / 2, 5 + w / 2, d + 1, Blocks.SPRUCE_SLAB.defaultBlockState());
            b.set(1, 1, d - 1, Blocks.CRAFTING_TABLE);
            b.set(2, 1, d - 1, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
            b.set(3, 1, d - 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
            b.set(w - 1, 1, d - 1, Blocks.BOOKSHELF);
            b.set(4, 4, 5, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            reg("maison_bois", "Maison en bois", "Maisons", "Maison 9x11 avec toit en pente, four, coffre et etabli.", b);
        }
        // 4. Maison en pierre a etage
        {
            B b = new B();
            int s = 9;
            b.fill(0, 0, 0, s, 0, s, Blocks.STONE_BRICKS);
            b.walls(0, 1, 0, s, 8, s, Blocks.STONE_BRICKS);
            for (int[] c : new int[][]{{0, 0}, {s, 0}, {0, s}, {s, s}}) b.fill(c[0], 1, c[1], c[0], 8, c[1], Blocks.POLISHED_ANDESITE);
            b.air(1, 1, 1, s - 1, 8, s - 1);
            b.fill(1, 4, 1, s - 1, 4, s - 1, Blocks.OAK_PLANKS);
            b.air(s - 2, 4, 1, s - 1, 4, 3);
            for (int i = 0; i < 3; i++) b.set(s - 1, 1 + i, 3 - i + 0, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            b.fill(0, 9, 0, s, 9, s, Blocks.STONE_BRICK_SLAB.defaultBlockState());
            b.door(4, 1, 0, Blocks.SPRUCE_DOOR, Direction.NORTH);
            for (int y : new int[]{2, 6}) for (int i = 2; i < s; i += 3) { b.set(i, y, 0, Blocks.GLASS); b.set(i, y, s, Blocks.GLASS); b.set(0, y, i, Blocks.GLASS); b.set(s, y, i, Blocks.GLASS); }
            b.set(1, 1, s - 1, Blocks.CRAFTING_TABLE).set(2, 1, s - 1, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
            b.set(4, 3, 4, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            b.set(4, 7, 4, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            reg("maison_pierre", "Maison en pierre", "Maisons", "Maison 10x10 en briques de pierre, 2 etages avec escalier.", b);
        }
        // 5. Maison moderne
        {
            B b = new B();
            b.fill(0, 0, 0, 12, 0, 8, Blocks.SMOOTH_STONE);
            b.walls(0, 1, 0, 12, 4, 8, Blocks.WHITE_CONCRETE);
            b.air(1, 1, 1, 11, 4, 7);
            b.fill(1, 1, 0, 11, 3, 0, Blocks.GLASS);
            b.fill(12, 2, 2, 12, 3, 6, Blocks.GLASS);
            b.fill(-1, 5, -1, 13, 5, 9, Blocks.GRAY_CONCRETE);
            b.fill(0, 6, 0, 6, 9, 8, Blocks.WHITE_CONCRETE);
            b.air(1, 6, 1, 5, 9, 7);
            b.fill(6, 7, 2, 6, 8, 6, Blocks.GLASS);
            b.fill(-1, 10, -1, 7, 10, 9, Blocks.GRAY_CONCRETE);
            b.door(6, 1, 0, Blocks.IRON_DOOR, Direction.NORTH);
            b.set(6, 0, -1, Blocks.STONE_PRESSURE_PLATE.defaultBlockState()).set(6, 1, -1, Blocks.STONE_PRESSURE_PLATE);
            b.set(6, 0, -1, Blocks.SMOOTH_STONE);
            for (int x = 2; x <= 10; x += 4) b.set(x, 4, 4, Blocks.SEA_LANTERN);
            reg("maison_moderne", "Maison moderne", "Maisons", "Villa en beton blanc et verre avec terrasse sur le toit.", b);
        }
        // 6. Tour de guet
        {
            B b = new B();
            b.walls(0, 0, 0, 4, 14, 4, Blocks.STONE_BRICKS);
            b.air(1, 0, 1, 3, 14, 3);
            b.fill(1, 0, 1, 3, 0, 3, Blocks.COBBLESTONE);
            for (int y = 1; y <= 14; y++) b.set(2, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            b.air(2, 1, 0, 2, 2, 0);
            b.fill(-1, 15, -1, 5, 15, 5, Blocks.SPRUCE_PLANKS);
            b.air(2, 15, 3, 2, 15, 3);
            for (int x = -1; x <= 5; x++) { b.set(x, 16, -1, Blocks.SPRUCE_FENCE); b.set(x, 16, 5, Blocks.SPRUCE_FENCE); }
            for (int z = -1; z <= 5; z++) { b.set(-1, 16, z, Blocks.SPRUCE_FENCE); b.set(5, 16, z, Blocks.SPRUCE_FENCE); }
            b.set(-1, 17, -1, Blocks.TORCH).set(5, 17, -1, Blocks.TORCH).set(-1, 17, 5, Blocks.TORCH).set(5, 17, 5, Blocks.TORCH);
            reg("tour_guet", "Tour de guet", "Defense", "Tour de 17 blocs avec echelle et plateforme d'observation.", b);
        }
        // 7. Phare
        {
            B b = new B();
            for (int y = 0; y <= 18; y++) b.cylinder(3, 3, 3, y, y, (y / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE, true);
            b.cylinder(3, 3, 3, 0, 0, Blocks.STONE_BRICKS, false);
            b.cylinder(3, 3, 4, 19, 19, Blocks.STONE_BRICK_SLAB, false);
            b.cylinder(3, 3, 1.4, 20, 21, Blocks.GLASS, true);
            b.set(3, 20, 3, Blocks.GLOWSTONE).set(3, 21, 3, Blocks.GLOWSTONE);
            b.cylinder(3, 3, 2, 22, 22, Blocks.QUARTZ_SLAB, false);
            for (int y = 1; y <= 19; y++) b.set(3, y, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            b.air(3, 1, 0, 3, 2, 0);
            reg("phare", "Phare", "Decoration", "Phare raye rouge et blanc avec lumiere au sommet.", b);
        }
        // 8. Chateau
        {
            B b = new B();
            int s = 20;
            b.fill(0, 0, 0, s, 0, s, Blocks.STONE_BRICKS);
            b.walls(0, 1, 0, s, 6, s, Blocks.STONE_BRICKS);
            for (int i = 0; i <= s; i += 2) { b.set(i, 7, 0, Blocks.STONE_BRICKS); b.set(i, 7, s, Blocks.STONE_BRICKS); b.set(0, 7, i, Blocks.STONE_BRICKS); b.set(s, 7, i, Blocks.STONE_BRICKS); }
            for (int[] c : new int[][]{{0, 0}, {s - 4, 0}, {0, s - 4}, {s - 4, s - 4}}) {
                b.walls(c[0], 0, c[1], c[0] + 4, 10, c[1] + 4, Blocks.COBBLESTONE);
                b.fill(c[0], 11, c[1], c[0] + 4, 11, c[1] + 4, Blocks.COBBLESTONE);
                for (int i = 0; i <= 4; i += 2) { b.set(c[0] + i, 12, c[1], Blocks.COBBLESTONE); b.set(c[0] + i, 12, c[1] + 4, Blocks.COBBLESTONE); b.set(c[0], 12, c[1] + i, Blocks.COBBLESTONE); b.set(c[0] + 4, 12, c[1] + i, Blocks.COBBLESTONE); }
                b.air(c[0] + 1, 1, c[1] + 1, c[0] + 3, 10, c[1] + 3);
            }
            b.air(9, 1, 0, 11, 4, 0);
            b.fill(9, 5, 0, 11, 5, 0, Blocks.CHISELED_STONE_BRICKS);
            b.set(8, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            b.set(12, 3, -1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            reg("chateau", "Chateau fort", "Defense", "Enceinte 21x21 avec 4 tours crenelees et une grande porte.", b);
        }
        // 9. Rempart
        {
            B b = new B();
            b.fill(0, 0, 0, 20, 3, 1, Blocks.COBBLESTONE);
            for (int x = 0; x <= 20; x += 2) b.set(x, 4, 0, Blocks.COBBLESTONE_WALL);
            b.fill(0, 4, 1, 20, 4, 1, Blocks.COBBLESTONE_SLAB);
            reg("rempart", "Rempart", "Defense", "Mur defensif de 21 blocs avec creneaux.", b);
        }
        // 10. Pont
        {
            B b = new B();
            b.fill(0, 0, 0, 2, 0, 15, Blocks.OAK_PLANKS);
            for (int z = 0; z <= 15; z++) { b.set(-1, 0, z, Blocks.OAK_LOG); b.set(3, 0, z, Blocks.OAK_LOG); b.set(-1, 1, z, Blocks.OAK_FENCE); b.set(3, 1, z, Blocks.OAK_FENCE); }
            for (int z = 0; z <= 15; z += 5) { b.set(-1, 2, z, Blocks.TORCH); b.set(3, 2, z, Blocks.TORCH); }
            reg("pont", "Pont en bois", "Utilitaire", "Pont de 16 blocs de long avec rambardes et torches.", b);
        }
        // 11. Ferme a ble
        {
            B b = new B();
            for (int x = 0; x <= 8; x++) for (int z = 0; z <= 8; z++) b.set(x, 0, z, Blocks.FARMLAND);
            b.set(4, 0, 4, Blocks.WATER);
            for (int i = -1; i <= 9; i++) { b.set(i, 1, -1, Blocks.OAK_FENCE); b.set(i, 1, 9, Blocks.OAK_FENCE); b.set(-1, 1, i, Blocks.OAK_FENCE); b.set(9, 1, i, Blocks.OAK_FENCE); }
            b.set(4, 1, -1, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
            b.set(4, 1, 4, Blocks.LILY_PAD);
            b.set(-1, 2, -1, Blocks.TORCH).set(9, 2, -1, Blocks.TORCH).set(-1, 2, 9, Blocks.TORCH).set(9, 2, 9, Blocks.TORCH);
            reg("ferme_ble", "Ferme a ble", "Ferme", "Champ 9x9 irrigue et cloture (labourer la terre ensuite).", b);
        }
        // 12. Enclos a animaux
        {
            B b = new B();
            for (int i = 0; i <= 11; i++) { b.set(i, 0, 0, Blocks.OAK_FENCE); b.set(i, 0, 11, Blocks.OAK_FENCE); b.set(0, 0, i, Blocks.OAK_FENCE); b.set(11, 0, i, Blocks.OAK_FENCE); }
            b.set(5, 0, 0, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
            b.set(0, 1, 0, Blocks.TORCH).set(11, 1, 0, Blocks.TORCH).set(0, 1, 11, Blocks.TORCH).set(11, 1, 11, Blocks.TORCH);
            b.set(2, 0, 9, Blocks.HAY_BLOCK).set(9, 0, 9, Blocks.HAY_BLOCK);
            reg("enclos", "Enclos a animaux", "Ferme", "Enclos 12x12 avec portillon et bottes de foin.", b);
        }
        // 13. Puits
        {
            B b = new B();
            b.fill(0, -3, 0, 4, -1, 4, Blocks.COBBLESTONE);
            b.fill(1, -3, 1, 3, -1, 3, Blocks.WATER);
            b.walls(0, 0, 0, 4, 0, 4, Blocks.COBBLESTONE);
            b.fill(1, 0, 1, 3, 0, 3, Blocks.WATER);
            for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.fill(c[0], 1, c[1], c[0], 2, c[1], Blocks.OAK_FENCE);
            b.fill(0, 3, 0, 4, 3, 4, Blocks.OAK_SLAB);
            reg("puits", "Puits", "Decoration", "Puits de village 5x5 avec toit.", b);
        }
        // 14. Portail du Nether
        {
            B b = new B();
            for (int x = 1; x <= 2; x++) { b.set(x, 0, 0, Blocks.OBSIDIAN); b.set(x, 4, 0, Blocks.OBSIDIAN); }
            for (int y = 1; y <= 3; y++) { b.set(0, y, 0, Blocks.OBSIDIAN); b.set(3, y, 0, Blocks.OBSIDIAN); }
            // coins en pierre : ils servent d'appui pour poser les colonnes en survie
            b.set(0, 0, 0, Blocks.COBBLESTONE).set(3, 0, 0, Blocks.COBBLESTONE).set(0, 4, 0, Blocks.COBBLESTONE).set(3, 4, 0, Blocks.COBBLESTONE);
            b.air(1, 1, 0, 2, 3, 0);
            reg("portail_nether", "Portail du Nether", "Utilitaire", "Cadre de 10 obsidiennes (+4 pierres aux coins). A allumer au briquet.", b);
        }
        // 15. Pyramide
        {
            B b = new B();
            int n = 15;
            for (int y = 0; y <= n / 2; y++) b.walls(y, y, y, n - 1 - y, y, n - 1 - y, Blocks.SANDSTONE);
            b.fill(0, 0, 0, n - 1, 0, n - 1, Blocks.SANDSTONE);
            b.set(n / 2, n / 2, n / 2, Blocks.GOLD_BLOCK);
            b.air(n / 2, 1, 0, n / 2, 2, 1);
            reg("pyramide", "Pyramide", "Decoration", "Pyramide de gres 15x15 avec sommet en or.", b);
        }
        // 16. Igloo
        {
            B b = new B();
            b.sphere(4, 0, 4, 4, Blocks.SNOW_BLOCK, true, true);
            b.fill(0, 0, 0, 8, 0, 8, Blocks.AIR);
            b.cylinder(4, 4, 4, 0, 0, Blocks.SNOW_BLOCK, false);
            b.air(4, 1, 0, 4, 2, 0);
            b.set(4, 2, 4, Blocks.AIR);
            b.set(6, 1, 4, Blocks.RED_CARPET);
            b.set(4, 3, 4, Blocks.AIR);
            reg("igloo", "Igloo", "Maisons", "Igloo en neige, rond et douillet.", b);
        }
        // 17. Dome de verre
        {
            B b = new B();
            b.cylinder(7, 7, 7, 0, 0, Blocks.SMOOTH_STONE, false);
            b.sphere(7, 0, 7, 7, Blocks.GLASS, true, true);
            b.cylinder(7, 7, 7, 0, 0, Blocks.SMOOTH_STONE, false);
            b.air(7, 1, 0, 7, 2, 0);
            b.set(7, 6, 7, Blocks.SEA_LANTERN);
            reg("dome_verre", "Dome de verre", "Decoration", "Grand dome en verre de 15 blocs de diametre.", b);
        }
        // 18. Sphere de pierre
        {
            B b = new B();
            b.sphere(5, 5, 5, 5, Blocks.STONE, true, false);
            reg("sphere", "Sphere de pierre", "Formes", "Sphere creuse de rayon 5.", b);
        }
        // 19. Escalier en colimacon
        {
            B b = new B();
            int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
            for (int y = 0; y < 20; y++) {
                b.set(2, y, 2, Blocks.STONE_BRICKS);
                int[] r = ring[y % ring.length];
                b.set(2 + r[0], y, 2 + r[1], Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
            reg("colimacon", "Escalier en colimacon", "Utilitaire", "Escalier tournant de 20 blocs de haut.", b);
        }
        // 20. Salle d'enchantement
        {
            B b = new B();
            b.fill(0, 0, 0, 6, 0, 6, Blocks.DARK_OAK_PLANKS);
            b.set(3, 1, 3, Blocks.ENCHANTING_TABLE);
            int placed = 0;
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
                boolean ring = x == 1 || x == 5 || z == 1 || z == 5;
                if (!ring || (x == 3 && z == 1)) continue;
                if (placed < 15) { b.set(x, 1, z, Blocks.BOOKSHELF); placed++; }
                if (placed < 15 && (x == 1 || x == 5) && z != 3) { b.set(x, 2, z, Blocks.BOOKSHELF); placed++; }
            }
            b.set(3, 1, 5, Blocks.ANVIL);
            b.set(0, 1, 0, Blocks.LANTERN).set(6, 1, 0, Blocks.LANTERN).set(0, 1, 6, Blocks.LANTERN).set(6, 1, 6, Blocks.LANTERN);
            reg("enchantement", "Salle d'enchantement", "Utilitaire", "Table d'enchantement entouree de 15 bibliotheques (niveau 30).", b);
        }
        // 21. Bunker
        {
            B b = new B();
            b.fill(0, 0, 0, 7, 4, 7, Blocks.DEEPSLATE_BRICKS);
            b.air(1, 1, 1, 6, 3, 6);
            b.door(3, 1, 0, Blocks.IRON_DOOR, Direction.NORTH);
            b.set(3, 1, -1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            b.set(3, 0, -1, Blocks.DEEPSLATE_BRICKS);
            b.set(2, 1, -1, Blocks.AIR);
            b.set(3, 1, -1, Blocks.AIR);
            b.set(2, 2, 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
            b.fill(1, 1, 6, 6, 1, 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
            b.set(3, 3, 3, Blocks.GLOWSTONE);
            reg("bunker", "Bunker", "Defense", "Bunker blinde en briques d'ardoise avec porte en fer et coffres.", b);
        }
        // 22. Arene
        {
            B b = new B();
            b.cylinder(10, 10, 10, 0, 0, Blocks.SAND, false);
            b.cylinder(10, 10, 10, 1, 3, Blocks.STONE_BRICKS, true);
            b.cylinder(10, 10, 11, 4, 4, Blocks.STONE_BRICK_SLAB, true);
            b.air(10, 1, 0, 10, 2, 0);
            for (int a = 0; a < 360; a += 45) {
                int x = 10 + (int) Math.round(Math.cos(Math.toRadians(a)) * 10), z = 10 + (int) Math.round(Math.sin(Math.toRadians(a)) * 10);
                b.set(x, 4, z, Blocks.GLOWSTONE);
            }
            reg("arene", "Arene", "Decoration", "Arene circulaire de combat (21 blocs).", b);
        }
        // 23. Labyrinthe
        {
            B b = new B();
            int n = 10; // cellules
            int size = n * 2 + 1;
            boolean[][] open = new boolean[size][size];
            Random rnd = new Random(1337);
            Deque<int[]> st = new ArrayDeque<>();
            st.push(new int[]{1, 1});
            open[1][1] = true;
            while (!st.isEmpty()) {
                int[] c = st.peek();
                List<int[]> nb = new ArrayList<>();
                for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                    int x = c[0] + d[0], z = c[1] + d[1];
                    if (x > 0 && z > 0 && x < size - 1 && z < size - 1 && !open[x][z]) nb.add(new int[]{x, z, d[0] / 2, d[1] / 2});
                }
                if (nb.isEmpty()) { st.pop(); continue; }
                int[] k = nb.get(rnd.nextInt(nb.size()));
                open[c[0] + k[2]][c[1] + k[3]] = true;
                open[k[0]][k[1]] = true;
                st.push(new int[]{k[0], k[1]});
            }
            open[1][0] = true;
            open[size - 2][size - 1] = true;
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
                b.set(x, 0, z, Blocks.GRASS_BLOCK);
                if (!open[x][z]) { b.set(x, 1, z, leaves()); b.set(x, 2, z, leaves()); }
            }
            b.set(size - 2, 1, size - 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
            reg("labyrinthe", "Labyrinthe", "Decoration", "Labyrinthe de haies 21x21 avec un coffre au bout.", b);
        }
        // 24. Coeur en pixel art
        {
            B b = new B();
            String[] rows = {
                    ".XX...XX.",
                    "XXXX.XXXX",
                    "XXXXXXXXX",
                    "XXXXXXXXX",
                    ".XXXXXXX.",
                    "..XXXXX..",
                    "...XXX...",
                    "....X....",
            };
            for (int r = 0; r < rows.length; r++)
                for (int c = 0; c < rows[r].length(); c++)
                    if (rows[r].charAt(c) == 'X') b.set(c, rows.length - r, 0, r < 2 && (c == 2 || c == 6) ? Blocks.PINK_WOOL : Blocks.RED_WOOL);
            b.set(4, 0, 0, Blocks.STONE_BRICKS);
            reg("coeur", "Coeur geant", "Pixel art", "Coeur en laine rouge (pour Nath et Vavan <3).", b);
        }
        // 25. Arbre geant
        {
            B b = new B();
            b.fill(0, 0, 0, 1, 11, 1, Blocks.OAK_LOG);
            b.sphere(0, 12, 0, 5, Blocks.OAK_LEAVES, false, false);
            Map<BlockPos, BlockState> fix = new LinkedHashMap<>();
            b.m.forEach((p, s) -> fix.put(p, s.is(Blocks.OAK_LEAVES) ? leaves() : s));
            b.m.clear();
            b.m.putAll(fix);
            b.fill(0, 0, 0, 1, 12, 1, Blocks.OAK_LOG);
            reg("arbre_geant", "Arbre geant", "Decoration", "Chene geant 2x2 avec un grand feuillage.", b);
        }
        // 26. Plateforme
        {
            B b = new B();
            b.fill(0, 0, 0, 15, 0, 15, Blocks.STONE);
            for (int x = 0; x <= 15; x += 5) for (int z = 0; z <= 15; z += 5) b.set(x, 1, z, Blocks.TORCH);
            reg("plateforme", "Plateforme 16x16", "Utilitaire", "Grande dalle de pierre eclairee (base de construction).", b);
        }
        // 27. Fontaine
        {
            B b = new B();
            b.cylinder(4, 4, 4, 0, 0, Blocks.QUARTZ_BLOCK, false);
            b.cylinder(4, 4, 4, 1, 1, Blocks.QUARTZ_SLAB, true);
            b.cylinder(4, 4, 3, 1, 1, Blocks.WATER, false);
            b.fill(4, 1, 4, 4, 3, 4, Blocks.QUARTZ_PILLAR);
            b.set(4, 4, 4, Blocks.WATER);
            reg("fontaine", "Fontaine", "Decoration", "Fontaine en quartz avec jet d'eau.", b);
        }
        // 28. Ferme a cannes a sucre
        {
            B b = new B();
            for (int x = 0; x <= 8; x++) {
                b.set(x, 0, 0, Blocks.SAND);
                b.set(x, 0, 1, Blocks.WATER);
                b.set(x, 0, 2, Blocks.SAND);
                b.set(x, 1, 0, Blocks.SUGAR_CANE);
                b.set(x, 1, 2, Blocks.SUGAR_CANE);
            }
            b.walls(-1, -1, -1, 9, 0, 3, Blocks.COBBLESTONE);
            b.fill(0, -1, 0, 8, -1, 2, Blocks.COBBLESTONE);
            reg("ferme_canne", "Ferme a cannes a sucre", "Ferme", "Deux rangees de cannes a sucre irriguees.", b);
        }
    }
}
