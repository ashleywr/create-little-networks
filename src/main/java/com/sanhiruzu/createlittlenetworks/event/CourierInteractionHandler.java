package com.sanhiruzu.createlittlenetworks.event;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.routing.CourierDispatcher;
import com.sanhiruzu.createlittlenetworks.routing.CourierNetworkData;
import com.sanhiruzu.createlittlenetworks.schedule.CourierSchedule;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationRegistry;
import dev.murad.shipping.entity.custom.DispatchableHeadVehicle;
import dev.murad.shipping.global.VehicleRegistrationData;
import dev.murad.shipping.item.NetworkChartItem;
import dev.murad.shipping.transport.NetworkChartReference;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = CreateLittleNetworks.MOD_ID)
public final class CourierInteractionHandler {
    private CourierInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof DispatchableHeadVehicle head) || !(event.getTarget() instanceof Entity vehicle)) return;
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof ScheduleItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                    || !(event.getLevel() instanceof ServerLevel level)) return;
            install(level, player, vehicle, head, held);
            return;
        }

        if (!held.isEmpty() || event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        CourierNetworkData data = CourierNetworkData.get(level.getServer());
        if (!data.isCourier(vehicle.getUUID())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        remove(level, player, vehicle, head, data);
    }

    private static void install(ServerLevel level, ServerPlayer player, Entity vehicle,
                                DispatchableHeadVehicle head, ItemStack stack) {
        Schedule schedule = ScheduleItem.getSchedule(player.registryAccess(), stack);
        if (schedule == null) {
            message(player, "Configure the schedule before applying it.", ChatFormatting.RED);
            return;
        }
        CourierSchedule.Result validation = CourierSchedule.validate(schedule);
        if (validation.value().isEmpty()) {
            message(player, validation.error(), ChatFormatting.RED);
            return;
        }
        CourierSchedule.Validated validated = validation.value().get();

        ItemStack chartStack = head.getRouteItemHandler().getStackInSlot(0);
        Optional<NetworkChartReference> chart = NetworkChartItem.getReference(chartStack);
        if (chart.isEmpty()) {
            message(player, "Install a Network Chart in the vehicle route slot first.", ChatFormatting.RED);
            return;
        }
        TransportNetwork network = TransportNetworkData.get(level.getServer()).get(chart.get().networkId()).orElse(null);
        if (network == null) {
            message(player, "The installed Network Chart references a missing network.", ChatFormatting.RED);
            return;
        }

        Optional<UUID> currentStation = currentStation(level, vehicle.getUUID());
        if (currentStation.isEmpty() || network.stationNode(currentStation.get()).isEmpty()) {
            message(player, "Apply the schedule while the vehicle is docked at a station on its Network Chart.", ChatFormatting.RED);
            return;
        }

        UUID home = currentStation.get();
        if (validated.usesDepot()) {
            List<UUID> depotMatches = new ArrayList<>();
            for (DockingStationRegistry.StationSnapshot station : DockingStationRegistry.getOrCreate(level).snapshots()) {
                if (network.stationNode(station.id()).isPresent()
                        && PackageItem.matchAddress(station.name(), validated.depotFilter())) {
                    depotMatches.add(station.id());
                }
            }
            if (depotMatches.size() != 1) {
                message(player, depotMatches.isEmpty() ? "No network station matches the depot filter."
                        : "The depot filter matches more than one network station.", ChatFormatting.RED);
                return;
            }
            home = depotMatches.getFirst();
            if (!currentStation.get().equals(home)) {
                message(player, "Apply a depot schedule while the vehicle is docked at that depot.", ChatFormatting.RED);
                return;
            }
            if (consistContainsPackages(level, vehicle.getUUID(), home)) {
                message(player, "Unload all packages before enrolling a depot courier.", ChatFormatting.RED);
                return;
            }
        }
        CourierNetworkData data = CourierNetworkData.get(level.getServer());
        if (data.isCourier(vehicle.getUUID())) {
            message(player, "This vehicle already has a courier schedule.", ChatFormatting.RED);
            return;
        }
        if (!head.getDispatchNavigation().claim(CourierDispatcher.CONTROLLER_ID, network.id())) {
            message(player, "Another dispatcher already controls this vehicle.", ChatFormatting.RED);
            return;
        }

        head.clearDispatchRoute();
        head.getDispatchNavigation().setTarget(CourierDispatcher.CONTROLLER_ID, currentStation.get(), true);
        head.setEngineOn(false);
        CourierNetworkData.CourierMode mode = validated.usesDepot()
                ? CourierNetworkData.CourierMode.DEPOT : CourierNetworkData.CourierMode.CREATE_COMPATIBLE;
        data.enroll(vehicle.getUUID(), level.dimension().location().toString(), network.id(), home,
                currentStation.get(), mode, validated.addressFilter(),
                (net.minecraft.nbt.CompoundTag) stack.copyWithCount(1)
                        .saveOptional(player.registryAccess()),
                validated.pickupDelay(), validated.deliveryDelay());
        UUID owner = head.getOwnerUUID().orElse(player.getUUID());
        VehicleRegistrationData.get(level.getServer()).register(vehicle, owner);
        stack.shrink(1);
        message(player, validated.usesDepot()
                ? "Depot courier enrolled and waiting."
                : "Package courier enrolled and waiting for work.", ChatFormatting.GREEN);
    }

    private static void remove(ServerLevel level, ServerPlayer player, Entity vehicle,
                               DispatchableHeadVehicle head, CourierNetworkData data) {
        CourierNetworkData.Courier courier = data.courier(vehicle.getUUID()).orElse(null);
        if (courier == null) return;
        if (data.jobForVehicle(vehicle.getUUID()).isPresent()) {
            message(player, "Finish the active delivery before removing the schedule.", ChatFormatting.RED);
            return;
        }
        Optional<UUID> currentStation = currentStation(level, vehicle.getUUID());
        boolean invalidLocation = currentStation.isEmpty()
                || courier.mode == CourierNetworkData.CourierMode.DEPOT
                && (!currentStation.get().equals(courier.homeStationId)
                || consistContainsPackages(level, vehicle.getUUID(), courier.homeStationId));
        if (invalidLocation) {
            message(player, courier.mode == CourierNetworkData.CourierMode.DEPOT
                    ? "The courier must be empty and docked at its depot."
                    : "The courier must be docked before removing its schedule.", ChatFormatting.RED);
            return;
        }

        ItemStack returned = ItemStack.parseOptional(player.registryAccess(), courier.schedule);
        if (!(returned.getItem() instanceof ScheduleItem)) {
            returned = AllItems.SCHEDULE.asStack();
        }
        if (!player.getInventory().add(returned)) player.drop(returned, false);
        data.removeCourier(vehicle.getUUID());
        head.setEngineOn(false);
        head.clearDispatchRoute();
        head.getDispatchNavigation().release(CourierDispatcher.CONTROLLER_ID);
        VehicleRegistrationData.get(level.getServer()).unregister(vehicle.getUUID());
        message(player, "Courier schedule removed.", ChatFormatting.GREEN);
    }

    private static Optional<UUID> currentStation(ServerLevel level, UUID vehicleId) {
        for (DockingStationRegistry.StationSnapshot station : DockingStationRegistry.getOrCreate(level).snapshots()) {
            for (var pos : station.members()) {
                if (level.getBlockEntity(pos) instanceof DockingStationBlockEntity dock
                        && dock.getDockedHeadVehicle() instanceof Entity headEntity
                        && headEntity.getUUID().equals(vehicleId)) return Optional.of(station.id());
            }
        }
        return Optional.empty();
    }

    private static boolean consistContainsPackages(ServerLevel level, UUID vehicleId, UUID stationId) {
        DockingStationRegistry.StationSnapshot station = DockingStationRegistry.getOrCreate(level)
                .snapshot(stationId).orElse(null);
        if (station == null) return false;
        for (var pos : station.members()) {
            if (!(level.getBlockEntity(pos) instanceof DockingStationBlockEntity dock)
                    || !(dock.getDockedHeadVehicle() instanceof Entity headEntity)
                    || !headEntity.getUUID().equals(vehicleId)) continue;
            IItemHandler cargo = dock.getItemHandler();
            for (int slot = 0; slot < cargo.getSlots(); slot++) {
                if (PackageItem.isPackage(cargo.getStackInSlot(slot))) return true;
            }
        }
        return false;
    }

    private static void message(ServerPlayer player, String value, ChatFormatting color) {
        player.displayClientMessage(Component.literal(value).withStyle(color), true);
    }
}
