package com.sanhiruzu.createlittlenetworks.routing;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import dev.murad.shipping.api.DockingStationService;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationRegistry;
import dev.murad.shipping.entity.custom.DispatchableHeadVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Bridges the LL dock lifecycle to endpoint discovery, transfer, and dispatch. */
public final class CourierDockService implements DockingStationService {
    public static final CourierDockService INSTANCE = new CourierDockService();
    private static final int INTERVAL = 5;

    private CourierDockService() {}

    @Override
    public void tick(ServerLevel level, DockingStationBlockEntity dock) {
        if (Math.floorMod(level.getGameTime() + dock.getBlockPos().asLong(), INTERVAL) != 0) return;
        UUID stationId = dock.getStationId();
        if (stationId == null) return;

        CourierNetworkData data = CourierNetworkData.get(level.getServer());
        discoverDirectPackagers(level, dock, stationId, data);
        for (CourierNetworkData.Endpoint endpoint : data.endpointsAtDock(
                level.dimension().location().toString(), dock.getBlockPos())) {
            data.observe(endpoint, PackageEndpointAccess.observeOutbound(level, endpoint), level.getGameTime());
        }

        if (dock.isOccupied()) {
            var resolvedHead = dock.getDockedHeadVehicle();
            DispatchableHeadVehicle head = resolvedHead instanceof DispatchableHeadVehicle dispatchable
                    ? dispatchable : null;
            if (head instanceof net.minecraft.world.entity.Entity entity && data.isCourier(entity.getUUID())) {
                if (isPrimaryDock(level, dock, entity)) CourierDispatcher.processDock(level, dock, head);
            } else {
                DockPackageRouter.tick(dock);
            }
        }
        CourierDispatcher.tick(level.getServer(), level.getGameTime());
    }

    private static boolean isPrimaryDock(ServerLevel level, DockingStationBlockEntity candidate, Entity head) {
        UUID stationId = candidate.getStationId();
        if (stationId == null) return false;
        return DockingStationRegistry.getOrCreate(level).snapshot(stationId).stream()
                .flatMap(snapshot -> snapshot.members().stream())
                .sorted(java.util.Comparator.comparingLong(BlockPos::asLong))
                .filter(pos -> level.getBlockEntity(pos) instanceof DockingStationBlockEntity member
                        && member.isOccupied()
                        && member.getDockedHeadVehicle() instanceof Entity memberHead
                        && memberHead.getUUID().equals(head.getUUID()))
                .findFirst().filter(candidate.getBlockPos()::equals).isPresent();
    }

    private static void discoverDirectPackagers(ServerLevel level, DockingStationBlockEntity dock,
                                                UUID stationId, CourierNetworkData data) {
        String address = DockingStationRegistry.getOrCreate(level).snapshot(stationId)
                .map(DockingStationRegistry.StationSnapshot::name).orElse("").trim();
        String dimension = level.dimension().location().toString();
        for (Direction direction : Direction.values()) {
            BlockPos portPos = dock.getBlockPos().relative(direction);
            if (level.getBlockEntity(portPos) instanceof PackagerBlockEntity packager
                    && DockPackageRouter.targetsDock(packager, dock.getBlockPos())) {
                data.registerEndpoint(dimension, dock.getBlockPos(), portPos, stationId,
                        CourierNetworkData.EndpointKind.PACKAGER, address, level.getGameTime());
            } else if (!(level.getBlockEntity(portPos) instanceof PackagerBlockEntity packager)
                    || !(level.getBlockEntity(DockPackageRouter.targetPosition(packager))
                    instanceof DockingStationBlockEntity)) {
                data.removeEndpoint(dimension, portPos, CourierNetworkData.EndpointKind.PACKAGER);
            }
        }
    }
}
