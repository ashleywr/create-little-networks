package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PanelOrientationTest {
    @Test
    void tileCoordinatesRoundTripForEveryFace() {
        BlockPos origin = new BlockPos(10, 20, 30);
        for (Direction facing : Direction.values()) {
            assertNotEquals(PanelOrientation.right(facing).getAxis(), PanelOrientation.up(facing).getAxis());
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    BlockPos world = PanelOrientation.offset(origin, facing, x, y);
                    assertEquals(x, PanelOrientation.tileX(origin, world, facing), facing + " x");
                    assertEquals(y, PanelOrientation.tileY(origin, world, facing), facing + " y");
                }
            }
        }
    }
}
