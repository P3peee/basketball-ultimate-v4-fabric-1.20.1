package com.example.basketball.logic;

import com.example.basketball.block.HoopBlock;
import com.example.basketball.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Зимняя арена: крытый зал со снежной крышей, паркетный корт 28x15, две стойки,
 * трибуны с двух сторон и табло над центром. Координаты (x,z) - клетки корта,
 * y=0 - уровень пола. Корт вытянут вдоль оси X.
 */
public final class ArenaBuilder {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int X0 = -10, X1 = 37, Z0 = -8, Z1 = 22, ROOF = 14;

    private final ServerWorld w;
    private final int ox, oy, oz;

    private ArenaBuilder(ServerWorld w, int ox, int oy, int oz) {
        this.w = w; this.ox = ox; this.oy = oy; this.oz = oz;
    }

    /** Строит арену так, чтобы игрок оказался в центре корта. */
    public static BlockPos build(ServerWorld w, BlockPos feet) {
        ArenaBuilder a = new ArenaBuilder(w, feet.getX() - 14, feet.getY() - 1, feet.getZ() - 7);
        a.buildAll();
        return new BlockPos(a.ox, a.oy, a.oz);
    }

    private void set(int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(ox + x, oy + y, oz + z), s, FLAGS);
    }

    private void set(int x, int y, int z, Block b) { set(x, y, z, b.getDefaultState()); }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        BlockState s = b.getDefaultState();
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    set(x, y, z, s);
    }

    private void buildAll() {
        // фундамент, снежная площадка и очистка объёма
        fill(X0 - 5, -3, Z0 - 5, X1 + 5, -1, Z1 + 5, Blocks.STONE);
        fill(X0 - 5, 0, Z0 - 5, X1 + 5, 0, Z1 + 5, Blocks.SNOW_BLOCK);
        fill(X0 - 5, 1, Z0 - 5, X1 + 5, 3, Z1 + 5, Blocks.AIR);
        fill(X0, 1, Z0, X1, ROOF + 2, Z1, Blocks.AIR);

        // пол зала и корт
        fill(X0, 0, Z0, X1, 0, Z1, Blocks.LIGHT_GRAY_CONCRETE);
        fill(0, 0, 0, 27, 0, 14, Blocks.BIRCH_PLANKS);
        drawCourtMarkings();

        buildWalls();
        buildRoof();
        buildBleachers();
        buildHoop(false);
        buildHoop(true);
        buildScoreboard();
    }

    // ---------- разметка ----------
    private void drawCourtMarkings() {
        Block line = Blocks.WHITE_CONCRETE;
        Block key = Blocks.LIGHT_BLUE_CONCRETE;

        for (int x = 0; x <= 27; x++) { set(x, 0, 0, line); set(x, 0, 14, line); }
        for (int z = 0; z <= 14; z++) { set(0, 0, z, line); set(27, 0, z, line); set(13, 0, z, line); }

        for (int x = 0; x <= 27; x++) {
            for (int z = 0; z <= 14; z++) {
                double d = Math.hypot(x + 0.5 - 14.0, z + 0.5 - 7.5);
                if (Math.abs(d - 3.0) < 0.55) set(x, 0, z, line);
                else if (d < 1.1) set(x, 0, z, key);
            }
        }
        drawHalf(false, line, key);
        drawHalf(true, line, key);
    }

    private static int mx(int x, boolean mirror) { return mirror ? 27 - x : x; }

    private void drawHalf(boolean mirror, Block line, Block key) {
        for (int x = 1; x <= 5; x++)
            for (int z = 5; z <= 9; z++)
                set(mx(x, mirror), 0, z, key);
        for (int x = 0; x <= 6; x++) {
            set(mx(x, mirror), 0, 4, line);
            set(mx(x, mirror), 0, 10, line);
        }
        for (int z = 4; z <= 10; z++) set(mx(6, mirror), 0, z, line);

        // полукруг штрафного
        for (int x = 7; x <= 10; x++)
            for (int z = 4; z <= 11; z++) {
                double d = Math.hypot(x + 0.5 - 6.5, z + 0.5 - 7.5);
                if (Math.abs(d - 2.4) < 0.55) set(mx(x, mirror), 0, z, line);
            }

        // трёхочковая линия (радиус 7.3 от центра кольца)
        for (int x = 0; x <= 14; x++)
            for (int z = 2; z <= 12; z++) {
                double d = Math.hypot(x + 0.5 - 1.5, z + 0.5 - 7.5);
                if (Math.abs(d - 7.3) < 0.52) set(mx(x, mirror), 0, z, line);
            }
        for (int x = 0; x <= 6; x++) {
            set(mx(x, mirror), 0, 2, line);
            set(mx(x, mirror), 0, 12, line);
        }
    }

    // ---------- стены и крыша ----------
    private void buildWalls() {
        for (int y = 1; y <= ROOF - 1; y++) {
            for (int x = X0; x <= X1; x++) {
                set(x, y, Z0, wallBlock(y, (x - X0) % 4 != 0));
                set(x, y, Z1, wallBlock(y, (x - X0) % 4 != 0));
            }
            for (int z = Z0; z <= Z1; z++) {
                set(X0, y, z, wallBlock(y, (z - Z0) % 4 != 0));
                set(X1, y, z, wallBlock(y, (z - Z0) % 4 != 0));
            }
        }
        // входы в торцах зала
        for (int y = 1; y <= 4; y++)
            for (int z = 5; z <= 9; z++) {
                set(X0, y, z, Blocks.AIR);
                set(X1, y, z, Blocks.AIR);
            }
    }

    private Block wallBlock(int y, boolean windowColumn) {
        if (y <= 2) return Blocks.LIGHT_BLUE_CONCRETE;
        if (y >= 8 && y <= 10 && windowColumn) return Blocks.LIGHT_BLUE_STAINED_GLASS;
        return Blocks.WHITE_CONCRETE;
    }

    private void buildRoof() {
        fill(X0, ROOF, Z0, X1, ROOF, Z1, Blocks.WHITE_CONCRETE);
        fill(X0 - 1, ROOF + 1, Z0 - 1, X1 + 1, ROOF + 1, Z1 + 1, Blocks.SNOW_BLOCK);
        for (int x = X0 + 3; x <= X1 - 3; x += 4)
            for (int z = Z0 + 3; z <= Z1 - 3; z += 4)
                set(x, ROOF, z, Blocks.SEA_LANTERN);
    }

    // ---------- трибуны ----------
    private void buildBleachers() {
        for (int i = 0; i <= 4; i++) {
            for (int x = 1; x <= 26; x++) {
                boolean blue = ((x - 1) / 4) % 2 == 0;
                Block stairs = blue ? Blocks.PRISMARINE_BRICK_STAIRS : Blocks.QUARTZ_STAIRS;

                int zs = -2 - i;
                for (int y = 1; y <= i; y++) set(x, y, zs, Blocks.LIGHT_GRAY_CONCRETE);
                set(x, i + 1, zs, stairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));

                int zn = 16 + i;
                for (int y = 1; y <= i; y++) set(x, y, zn, Blocks.LIGHT_GRAY_CONCRETE);
                set(x, i + 1, zn, stairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            }
        }
        fill(1, 1, -7, 26, 5, -7, Blocks.LIGHT_GRAY_CONCRETE);
        fill(1, 1, 21, 26, 5, 21, Blocks.LIGHT_GRAY_CONCRETE);
    }

    // ---------- стойки ----------
    private void buildHoop(boolean right) {
        int hx = right ? 26 : 1;
        int armX = right ? 27 : 0;
        int poleX = right ? 28 : -1;
        Direction facing = right ? Direction.EAST : Direction.WEST;

        set(hx, 3, 7, ModBlocks.HOOP.getDefaultState().with(HoopBlock.FACING, facing));
        set(armX, 3, 7, Blocks.GRAY_CONCRETE);
        set(poleX, 1, 7, Blocks.RED_CONCRETE);
        set(poleX, 2, 7, Blocks.RED_CONCRETE);
        set(poleX, 3, 7, Blocks.GRAY_CONCRETE);
    }

    // ---------- табло ----------
    private void buildScoreboard() {
        fill(12, 9, 6, 15, 10, 8, Blocks.BLACK_CONCRETE);
        fill(13, 9, 5, 14, 10, 5, Blocks.SEA_LANTERN);
        fill(13, 9, 9, 14, 10, 9, Blocks.SEA_LANTERN);
        for (int y = 11; y <= ROOF - 1; y++) {
            set(12, y, 6, Blocks.CHAIN);
            set(15, y, 8, Blocks.CHAIN);
        }
    }
}
