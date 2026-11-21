package com.example.basketball.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Кольцо со щитом. FACING - в какую сторону смотрит игрок при установке,
 * то есть щит стоит ЗА кольцом. Отверстие 8x8 пикселей посередине блока.
 */
public class HoopBlock extends HorizontalFacingBlock {
    private static final VoxelShape RIM = VoxelShapes.union(
            Block.createCuboidShape(2, 8, 2, 3, 10, 14),
            Block.createCuboidShape(13, 8, 2, 14, 10, 14),
            Block.createCuboidShape(3, 8, 2, 13, 10, 3),
            Block.createCuboidShape(3, 8, 13, 13, 10, 14));

    private static final VoxelShape NORTH = VoxelShapes.union(RIM, Block.createCuboidShape(0, 0, 0, 16, 16, 2));
    private static final VoxelShape SOUTH = VoxelShapes.union(RIM, Block.createCuboidShape(0, 0, 14, 16, 16, 16));
    private static final VoxelShape WEST = VoxelShapes.union(RIM, Block.createCuboidShape(0, 0, 0, 2, 16, 16));
    private static final VoxelShape EAST = VoxelShapes.union(RIM, Block.createCuboidShape(14, 0, 0, 16, 16, 16));

    public HoopBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }
}
