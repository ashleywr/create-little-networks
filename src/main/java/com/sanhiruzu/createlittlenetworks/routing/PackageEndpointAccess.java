package com.sanhiruzu.createlittlenetworks.routing;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PackageEndpointAccess {
    private PackageEndpointAccess() {}

    static Map<String, Integer> observeOutbound(ServerLevel level, CourierNetworkData.Endpoint endpoint) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!level.isLoaded(endpoint.portPos)) return result;
        BlockEntity blockEntity = level.getBlockEntity(endpoint.portPos);
        if (endpoint.kind == CourierNetworkData.EndpointKind.PACKAGER
                && blockEntity instanceof PackagerBlockEntity packager) {
            addPackage(result, packager.heldBox, endpoint.address);
        } else if (endpoint.kind == CourierNetworkData.EndpointKind.POSTBOX
                && blockEntity instanceof PostboxBlockEntity postbox) {
            for (int slot = 0; slot < postbox.inventory.getSlots(); slot++) {
                addPackage(result, postbox.inventory.getStackInSlot(slot), postbox.getFilterString());
            }
        }
        return result;
    }

    private static void addPackage(Map<String, Integer> result, ItemStack stack, String localAddress) {
        if (!PackageItem.isPackage(stack)) return;
        if (localAddress != null && PackageItem.matchAddress(stack, localAddress)) return;
        String address = PackageItem.getAddress(stack);
        if (!address.isBlank()) result.merge(address, stack.getCount(), Integer::sum);
    }

    static boolean loadOne(ServerLevel level, CourierNetworkData.Endpoint endpoint,
                           IItemHandler cargo, String exactAddress) {
        if (!level.isLoaded(endpoint.portPos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(endpoint.portPos);
        if (endpoint.kind == CourierNetworkData.EndpointKind.PACKAGER
                && blockEntity instanceof PackagerBlockEntity packager) {
            return DockPackageRouter.collectOne(cargo, packager, exactAddress);
        }
        if (endpoint.kind == CourierNetworkData.EndpointKind.POSTBOX
                && blockEntity instanceof PostboxBlockEntity postbox) {
            return DockPackageRouter.collectOneFromPostbox(cargo, postbox, exactAddress);
        }
        return false;
    }

    static boolean unloadOne(ServerLevel level, CourierNetworkData.Endpoint endpoint,
                             IItemHandler cargo) {
        if (!level.isLoaded(endpoint.portPos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(endpoint.portPos);
        if (endpoint.kind == CourierNetworkData.EndpointKind.PACKAGER
                && blockEntity instanceof PackagerBlockEntity packager) {
            return DockPackageRouter.deliverOne(cargo, packager, endpoint.address);
        }
        if (endpoint.kind == CourierNetworkData.EndpointKind.POSTBOX
                && blockEntity instanceof PostboxBlockEntity postbox) {
            return DockPackageRouter.deliverOneToPostbox(cargo, postbox);
        }
        return false;
    }

    static boolean cargoContains(IItemHandler cargo, String exactAddress) {
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (PackageItem.isPackage(stack) && exactAddress.equals(PackageItem.getAddress(stack))) return true;
        }
        return false;
    }

    static boolean cargoContainsAnyPackage(IItemHandler cargo) {
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            if (PackageItem.isPackage(cargo.getStackInSlot(slot))) return true;
        }
        return false;
    }

    static Map<String, Integer> cargoAddresses(IItemHandler cargo) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (!PackageItem.isPackage(stack)) continue;
            String address = PackageItem.getAddress(stack);
            if (!address.isBlank()) result.merge(address, stack.getCount(), Integer::sum);
        }
        return result;
    }

    static IItemHandler consistCargo(ServerLevel level, DockingStationBlockEntity dock) {
        if (dock.getStationId() == null || !(dock.getDockedHeadVehicle() instanceof Entity head)) {
            return dock.getItemHandler();
        }
        var station = DockingStationRegistry.getOrCreate(level).snapshot(dock.getStationId()).orElse(null);
        if (station == null) return dock.getItemHandler();

        List<IItemHandler> handlers = new ArrayList<>();
        station.members().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).forEach(pos -> {
            if (!(level.getBlockEntity(pos) instanceof DockingStationBlockEntity member)
                    || !(member.getDockedHeadVehicle() instanceof Entity memberHead)
                    || !memberHead.getUUID().equals(head.getUUID())
                    || member.getItemHandler().getSlots() == 0) return;
            handlers.add(member.getItemHandler());
        });
        return handlers.size() == 1 ? handlers.getFirst()
                : handlers.isEmpty() ? dock.getItemHandler() : new CombinedHandler(handlers);
    }

    private static final class CombinedHandler implements IItemHandler {
        private final List<IItemHandler> handlers;
        private final int slots;

        private CombinedHandler(List<IItemHandler> handlers) {
            this.handlers = List.copyOf(handlers);
            this.slots = handlers.stream().mapToInt(IItemHandler::getSlots).sum();
        }

        @Override public int getSlots() { return slots; }

        @Override public @Nonnull ItemStack getStackInSlot(int slot) {
            SlotRef ref = resolve(slot);
            return ref == null ? ItemStack.EMPTY : ref.handler.getStackInSlot(ref.slot);
        }

        @Override public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            SlotRef ref = resolve(slot);
            return ref == null ? stack : ref.handler.insertItem(ref.slot, stack, simulate);
        }

        @Override public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            SlotRef ref = resolve(slot);
            return ref == null ? ItemStack.EMPTY : ref.handler.extractItem(ref.slot, amount, simulate);
        }

        @Override public int getSlotLimit(int slot) {
            SlotRef ref = resolve(slot);
            return ref == null ? 0 : ref.handler.getSlotLimit(ref.slot);
        }

        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            SlotRef ref = resolve(slot);
            return ref != null && ref.handler.isItemValid(ref.slot, stack);
        }

        private SlotRef resolve(int requested) {
            if (requested < 0) return null;
            int cursor = requested;
            for (IItemHandler handler : handlers) {
                if (cursor < handler.getSlots()) return new SlotRef(handler, cursor);
                cursor -= handler.getSlots();
            }
            return null;
        }

        private record SlotRef(IItemHandler handler, int slot) {}
    }
}
