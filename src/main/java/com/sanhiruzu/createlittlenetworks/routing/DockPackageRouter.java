package com.sanhiruzu.createlittlenetworks.routing;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;


/**
 * Moves Create packages between a directly attached packager and the cargo vehicle currently
 * exposed by a Little Logistics docking station.
 *
 * <p>The Little Logistics station name is the Create package address. This deliberately uses
 * Create's own glob-aware address matcher, so addresses such as {@code Harbor *} behave exactly
 * as they do on Create package ports.</p>
 */
public final class DockPackageRouter {
    private static final int ROUTING_INTERVAL_TICKS = 5;

    private DockPackageRouter() {
    }

    public static void tick(DockingStationBlockEntity dock) {
        if (!(dock.getLevel() instanceof ServerLevel level) || !dock.isOccupied()) {
            return;
        }

        if (Math.floorMod(level.getGameTime() + dock.getBlockPos().asLong(), ROUTING_INTERVAL_TICKS) != 0) {
            return;
        }

        IItemHandler cargo = PackageEndpointAccess.consistCargo(level, dock);
        if (cargo.getSlots() == 0) {
            return;
        }

        if (routeStationEndpoints(level, dock, cargo)) {
            dock.notifyTransfer();
        }
    }

    private static boolean routeStationEndpoints(ServerLevel level, DockingStationBlockEntity dock,
                                                 IItemHandler cargo) {
        if (dock.getStationId() == null) return false;
        boolean movedPackage = false;
        for (CourierNetworkData.Endpoint endpoint : CourierNetworkData.get(level.getServer())
                .endpointsAtStation(level.dimension().location().toString(), dock.getStationId())) {
            BlockPos portPos = endpoint.portPos;
            if (!level.isLoaded(portPos)) continue;

            BlockEntity blockEntity = level.getBlockEntity(portPos);
            boolean valid = endpoint.kind == CourierNetworkData.EndpointKind.POSTBOX
                    ? blockEntity instanceof PostboxBlockEntity postbox
                    && postbox.target instanceof LittleLogisticsPackagePortTarget target
                    && target.targetPosition(endpoint.portPos).equals(endpoint.dockPos)
                    : blockEntity instanceof PackagerBlockEntity packager
                    && targetsDock(packager, endpoint.dockPos);
            if (!valid) {
                CourierNetworkData.get(level.getServer()).removeEndpoint(
                        level.dimension().location().toString(), portPos,
                        endpoint.kind);
                continue;
            }

            while (PackageEndpointAccess.unloadOne(level, endpoint, cargo)) movedPackage = true;
            for (String address : PackageEndpointAccess.observeOutbound(level, endpoint).keySet()) {
                while (PackageEndpointAccess.loadOne(level, endpoint, cargo, address)) movedPackage = true;
            }
        }
        return movedPackage;
    }

    /** Mirrors Create train-station mail export: addressed cargo is deposited at this port. */
    static boolean deliverOneToPostbox(IItemHandler cargo, PostboxBlockEntity postbox) {
        String address = postbox.getFilterString();
        if (address == null) {
            return false;
        }

        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack candidate = cargo.extractItem(slot, 1, true);
            if (!PackageItem.isPackage(candidate) || !PackageItem.matchAddress(candidate, address)) {
                continue;
            }
            if (!ItemHandlerHelper.insertItemStacked(postbox.inventory, candidate, true).isEmpty()) {
                continue;
            }

            ItemStack extracted = cargo.extractItem(slot, 1, false);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(postbox.inventory, extracted, false);
            if (!remainder.isEmpty()) {
                ItemHandlerHelper.insertItemStacked(cargo, remainder, false);
                return false;
            }

            postbox.setChanged();
            postbox.spawnParticles();
            if (postbox.computerBehaviour != null) {
                postbox.computerBehaviour.prepareComputerEvent(new PackageEvent(extracted, "package_received"));
            }
            return true;
        }
        return false;
    }

    /** Mirrors Create train-station mail import: non-local packages are loaded for departure. */
    static boolean collectOneFromPostbox(IItemHandler cargo, PostboxBlockEntity postbox) {
        return collectOneFromPostbox(cargo, postbox, null);
    }

    static boolean collectOneFromPostbox(IItemHandler cargo, PostboxBlockEntity postbox, String expectedAddress) {
        String address = postbox.getFilterString();
        for (int slot = 0; slot < postbox.inventory.getSlots(); slot++) {
            ItemStack stack = postbox.inventory.getStackInSlot(slot);
            if (!PackageItem.isPackage(stack)
                    || address != null && PackageItem.matchAddress(stack, address)
                    || expectedAddress != null && !expectedAddress.equals(PackageItem.getAddress(stack))) {
                continue;
            }

            ItemStack candidate = stack.copyWithCount(1);
            if (!ItemHandlerHelper.insertItemStacked(cargo, candidate, true).isEmpty()) {
                continue;
            }

            ItemStack extracted = postbox.inventory.extractItem(slot, 1, false);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(cargo, extracted, false);
            if (!remainder.isEmpty()) {
                ItemHandlerHelper.insertItemStacked(postbox.inventory, remainder, false);
                return false;
            }

            postbox.setChanged();
            postbox.spawnParticles();
            if (postbox.computerBehaviour != null) {
                postbox.computerBehaviour.prepareComputerEvent(new PackageEvent(extracted, "package_sent"));
            }
            return true;
        }
        return false;
    }

    static boolean targetsDock(PackagerBlockEntity packager, BlockPos dockPos) {
        return targetPosition(packager).equals(dockPos);
    }

    static BlockPos targetPosition(PackagerBlockEntity packager) {
        BlockState state = packager.getBlockState();
        Direction facing = state.getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        return packager.getBlockPos().relative(facing.getOpposite());
    }

    static boolean deliverOne(IItemHandler cargo, PackagerBlockEntity packager, String stationAddress) {
        if (!packager.heldBox.isEmpty() || !packager.queuedExitingPackages.isEmpty()
                || packager.animationTicks != 0) {
            return false;
        }

        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack candidate = cargo.extractItem(slot, 1, true);
            if (!PackageItem.isPackage(candidate) || !PackageItem.matchAddress(candidate, stationAddress)) {
                continue;
            }

            ItemStack notAccepted = packager.inventory.insertItem(0, candidate, true);
            if (!notAccepted.isEmpty()) {
                continue;
            }

            ItemStack extracted = cargo.extractItem(slot, 1, false);
            if (extracted.isEmpty()) {
                continue;
            }

            ItemStack remainder = packager.inventory.insertItem(0, extracted, false);
            if (!remainder.isEmpty()) {
                // Capabilities are single-threaded, but restore the package if another integration
                // changed the packager between simulation and execution.
                ItemStack restoreRemainder = ItemHandlerHelper.insertItemStacked(cargo, remainder, false);
                if (!restoreRemainder.isEmpty()) {
                    BlockPos pos = packager.getBlockPos();
                    packager.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                            packager.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            restoreRemainder));
                }
                return false;
            }
            return true;
        }
        return false;
    }

    static boolean collectOne(IItemHandler cargo, PackagerBlockEntity packager) {
        return collectOne(cargo, packager, null);
    }

    static boolean collectOne(IItemHandler cargo, PackagerBlockEntity packager, String expectedAddress) {
        if (packager.animationTicks != 0 || packager.heldBox.isEmpty()) {
            return false;
        }

        ItemStack packageStack = packager.heldBox;
        if (!PackageItem.isPackage(packageStack)
                || expectedAddress != null && !expectedAddress.equals(PackageItem.getAddress(packageStack))) {
            return false;
        }

        ItemStack toInsert = packageStack.copyWithCount(1);
        if (!ItemHandlerHelper.insertItemStacked(cargo, toInsert, true).isEmpty()) {
            return false;
        }

        ItemStack remainder = ItemHandlerHelper.insertItemStacked(cargo, toInsert, false);
        if (!remainder.isEmpty()) {
            return false;
        }

        packageStack.shrink(1);
        packager.heldBox = packageStack.isEmpty() ? ItemStack.EMPTY : packageStack;
        packager.notifyUpdate();
        return true;
    }
}
