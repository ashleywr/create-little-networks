package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A stable viewer-facing coordinate system shared by placement, hit testing and rendering. */
public final class PanelOrientation {
    public static final int CELLS_PER_BLOCK = 8;

    private PanelOrientation() {}

    public static Direction right(Direction facing) {
        if (facing.getAxis().isHorizontal()) return facing.getCounterClockWise();
        return Direction.EAST;
    }

    public static Direction up(Direction facing) {
        if (facing.getAxis().isHorizontal()) return Direction.UP;
        return facing == Direction.UP ? Direction.NORTH : Direction.SOUTH;
    }

    public static BlockPos offset(BlockPos origin, Direction facing, int tileX, int tileY) {
        return origin.relative(right(facing), tileX).relative(up(facing), tileY);
    }

    public static int tileX(BlockPos origin, BlockPos pos, Direction facing) {
        return dot(pos.subtract(origin), right(facing));
    }

    public static int tileY(BlockPos origin, BlockPos pos, Direction facing) {
        return dot(pos.subtract(origin), up(facing));
    }

    public static int cellX(BlockPos pos, Vec3 hit, Direction facing) {
        return cell(localCoordinate(pos, hit, right(facing)));
    }

    public static int cellY(BlockPos pos, Vec3 hit, Direction facing) {
        return cell(localCoordinate(pos, hit, up(facing)));
    }

    private static int dot(BlockPos delta, Direction direction) {
        return delta.getX() * direction.getStepX()
                + delta.getY() * direction.getStepY()
                + delta.getZ() * direction.getStepZ();
    }

    private static double localCoordinate(BlockPos pos, Vec3 hit, Direction direction) {
        double coordinate;
        if (direction.getAxis() == Direction.Axis.X) coordinate = hit.x - pos.getX();
        else if (direction.getAxis() == Direction.Axis.Y) coordinate = hit.y - pos.getY();
        else coordinate = hit.z - pos.getZ();
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? coordinate : 1D - coordinate;
    }

    private static int cell(double coordinate) {
        return Mth.clamp((int) Math.floor(coordinate * CELLS_PER_BLOCK), 0, CELLS_PER_BLOCK - 1);
    }
}
