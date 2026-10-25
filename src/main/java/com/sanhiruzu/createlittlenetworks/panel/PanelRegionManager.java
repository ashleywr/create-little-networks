package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PanelRegionManager {
    public static final int MAX_BLOCKS_PER_AXIS = 8;
    private static final int MAX_SCAN = 512;

    private PanelRegionManager() {}

    public static void rebuildAround(Level level, BlockPos seed) {
        MimicPanelBlockEntity seedPanel = panel(level, seed);
        if (seedPanel == null) return;
        Direction facing = seedPanel.getBlockState().getValue(MimicPanelBlock.FACING);
        Set<BlockPos> component = scan(level, seed, facing, null);
        if (component.isEmpty()) return;
        rebuild(level, seed, facing, component, null);
    }

    public static void remove(Level level, BlockPos removedPos, PanelRegionSnapshot removedSnapshot) {
        if (removedSnapshot == null) return;
        Set<BlockPos> candidates = positions(removedSnapshot);
        candidates.remove(removedPos);
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (visited.contains(candidate) || panel(level, candidate) == null) continue;
            Set<BlockPos> component = scan(level, candidate, removedSnapshot.facing(), candidates);
            visited.addAll(component);
            rebuild(level, candidate, removedSnapshot.facing(), component, removedSnapshot);
        }
    }

    private static void rebuild(Level level, BlockPos seed, Direction facing, Set<BlockPos> positions,
                                PanelRegionSnapshot removedSnapshot) {
        if (positions.isEmpty()) return;
        BlockPos componentOrigin = lowerLeft(seed, facing, positions);
        Map<BlockPos, PanelRegionSnapshot> sources = sources(level, positions);
        if (removedSnapshot != null) sources.putIfAbsent(removedSnapshot.origin(), removedSnapshot);

        Set<UUID> networks = new HashSet<>();
        for (PanelRegionSnapshot source : sources.values()) {
            if (source != null && source.document().networkId() != null) networks.add(source.document().networkId());
        }
        if (networks.size() > 1) return;

        PanelDocument combined = combine(componentOrigin, facing, sources.values());
        Set<Cell> cells = new HashSet<>();
        for (BlockPos pos : positions) {
            cells.add(new Cell(PanelOrientation.tileX(componentOrigin, pos, facing),
                    PanelOrientation.tileY(componentOrigin, pos, facing)));
        }
        for (Rect rect : partition(cells)) {
            BlockPos origin = PanelOrientation.offset(componentOrigin, facing, rect.x, rect.y);
            PanelDocument clipped = combined.clipped(rect.x * PanelOrientation.CELLS_PER_BLOCK,
                    rect.y * PanelOrientation.CELLS_PER_BLOCK,
                    (rect.x + rect.width) * PanelOrientation.CELLS_PER_BLOCK,
                    (rect.y + rect.height) * PanelOrientation.CELLS_PER_BLOCK);
            assign(level, origin, facing, rect.width, rect.height, clipped);
        }
    }

    private static Map<BlockPos, PanelRegionSnapshot> sources(Level level, Set<BlockPos> positions) {
        Map<BlockPos, PanelRegionSnapshot> sources = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            MimicPanelBlockEntity panel = panel(level, pos);
            if (panel == null) continue;
            PanelRegionSnapshot snapshot = panel.snapshot();
            if (snapshot != null) sources.putIfAbsent(snapshot.origin(), snapshot);
        }
        return sources;
    }

    private static PanelDocument combine(BlockPos componentOrigin, Direction facing,
                                         java.util.Collection<PanelRegionSnapshot> sources) {
        PanelDocument combined = new PanelDocument();
        for (PanelRegionSnapshot source : sources) {
            if (source == null) continue;
            if (source.document().networkId() != null && !combined.isNetworkBound()) {
                combined.bindNetwork(source.document().networkId(), source.document().networkMode());
            }
            int dx = PanelOrientation.tileX(componentOrigin, source.origin(), facing) * PanelOrientation.CELLS_PER_BLOCK;
            int dy = PanelOrientation.tileY(componentOrigin, source.origin(), facing) * PanelOrientation.CELLS_PER_BLOCK;
            for (PanelWidget widget : source.document().widgets()) {
                if (combined.widget(widget.id()).isEmpty()) combined.add(widget.translated(dx, dy));
            }
        }
        return combined;
    }

    private static void assign(Level level, BlockPos origin, Direction facing, int width, int height,
                               PanelDocument document) {
        UUID regionId = UUID.randomUUID();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                BlockPos pos = PanelOrientation.offset(origin, facing, x, y);
                MimicPanelBlockEntity panel = panel(level, pos);
                if (panel != null) panel.configureRegion(regionId, origin, x, y, width, height,
                        x == 0 && y == 0 ? document : null);
            }
        }
    }

    private static List<Rect> partition(Set<Cell> input) {
        Set<Cell> remaining = new HashSet<>(input);
        List<Rect> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell start = remaining.stream().min(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x)).orElseThrow();
            Rect best = new Rect(start.x, start.y, 1, 1);
            int maxWidth = 0;
            while (maxWidth < MAX_BLOCKS_PER_AXIS && remaining.contains(new Cell(start.x + maxWidth, start.y))) maxWidth++;
            for (int width = 1; width <= maxWidth; width++) {
                for (int height = 1; height <= MAX_BLOCKS_PER_AXIS; height++) {
                    if (!containsRectangle(remaining, start.x, start.y, width, height)) break;
                    Rect candidate = new Rect(start.x, start.y, width, height);
                    if (candidate.area() > best.area() || candidate.area() == best.area() && candidate.width > best.width) {
                        best = candidate;
                    }
                }
            }
            Rect selected = best;
            for (int y = 0; y < selected.height; y++) {
                for (int x = 0; x < selected.width; x++) remaining.remove(new Cell(selected.x + x, selected.y + y));
            }
            result.add(selected);
        }
        return result;
    }

    private static boolean containsRectangle(Set<Cell> cells, int x, int y, int width, int height) {
        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < width; xx++) if (!cells.contains(new Cell(x + xx, y + yy))) return false;
        }
        return true;
    }

    private static Set<BlockPos> scan(Level level, BlockPos seed, Direction facing, Set<BlockPos> allowed) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        Direction right = PanelOrientation.right(facing);
        Direction up = PanelOrientation.up(facing);
        while (!queue.isEmpty() && found.size() < MAX_SCAN) {
            BlockPos pos = queue.removeFirst();
            if (!found.add(pos)) continue;
            for (Direction direction : List.of(right, right.getOpposite(), up, up.getOpposite())) {
                BlockPos next = pos.relative(direction);
                if (found.contains(next) || allowed != null && !allowed.contains(next)) continue;
                MimicPanelBlockEntity panel = panel(level, next);
                if (panel != null && panel.getBlockState().getValue(MimicPanelBlock.FACING) == facing) queue.add(next);
            }
        }
        return found;
    }

    private static BlockPos lowerLeft(BlockPos seed, Direction facing, Set<BlockPos> positions) {
        int minX = positions.stream().mapToInt(pos -> PanelOrientation.tileX(seed, pos, facing)).min().orElse(0);
        int minY = positions.stream().mapToInt(pos -> PanelOrientation.tileY(seed, pos, facing)).min().orElse(0);
        return PanelOrientation.offset(seed, facing, minX, minY);
    }

    private static Set<BlockPos> positions(PanelRegionSnapshot snapshot) {
        Set<BlockPos> result = new HashSet<>();
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                result.add(PanelOrientation.offset(snapshot.origin(), snapshot.facing(), x, y));
            }
        }
        return result;
    }

    private static MimicPanelBlockEntity panel(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MimicPanelBlockEntity panel ? panel : null;
    }

    private record Cell(int x, int y) {}
    private record Rect(int x, int y, int width, int height) { int area() { return width * height; } }
}
