package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.UUID;

public record PanelRegionSnapshot(UUID regionId, BlockPos origin, Direction facing,
                                  int width, int height, PanelDocument document) {
}
