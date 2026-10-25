package com.sanhiruzu.createlittlenetworks.routing;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.CourierConfig;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import dev.murad.shipping.entity.custom.DispatchableHeadVehicle;
import dev.murad.shipping.global.VehicleRegistrationData;
import dev.murad.shipping.transport.DispatchRoutePlan;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import net.createmod.catnip.data.Glob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

/** Server-authoritative Create-compatible package courier and optional depot dispatcher. */
public final class CourierDispatcher {
    public static final ResourceLocation CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath(
            CreateLittleNetworks.MOD_ID, "package_dispatcher");
    private static final long DISPATCH_INTERVAL = 20;

    private CourierDispatcher() {}

    public static void tick(MinecraftServer server, long gameTime) {
        CourierNetworkData data = CourierNetworkData.get(server);
        if (!dispatchDue(gameTime, data.lastDispatchTick)) return;
        data.lastDispatchTick = gameTime;
        pruneInvalidEndpoints(server, data);
        recoverTimedOutJobs(server, data, gameTime);

        List<CourierNetworkData.Courier> couriers = data.couriers().stream()
                .sorted(Comparator.comparing(courier -> courier.vehicleId.toString())).toList();
        for (CourierNetworkData.Courier courier : couriers) {
            if (data.jobForVehicle(courier.vehicleId).isPresent()) continue;
            dispatchOne(server, data, courier, gameTime);
        }
    }

    static boolean dispatchDue(long gameTime, long lastDispatchTick) {
        return lastDispatchTick == Long.MIN_VALUE
                || gameTime - lastDispatchTick >= DISPATCH_INTERVAL;
    }

    private static void dispatchOne(MinecraftServer server, CourierNetworkData data,
                                    CourierNetworkData.Courier courier, long gameTime) {
        Optional<LiveCourier> live = liveCourier(server, courier);
        if (live.isEmpty()) {
            data.setStatus(courier, CourierNetworkData.CourierStatus.WAITING_FOR_DOCK);
            return;
        }
        DispatchableHeadVehicle head = live.get().head;
        if (!head.getDispatchNavigation().isClaimedBy(CONTROLLER_ID)) return;

        TransportNetwork network = TransportNetworkData.get(server).get(courier.networkId).orElse(null);
        if (network == null) {
            data.setStatus(courier, CourierNetworkData.CourierStatus.MISSING_NETWORK);
            return;
        }

        UUID currentStation = courier.currentStationId;
        DockingStationBlockEntity currentDock = currentDock(live.get().level, currentStation, courier.vehicleId);
        if (currentDock == null) {
            data.setStatus(courier, CourierNetworkData.CourierStatus.WAITING_FOR_DOCK);
            return;
        }

        IItemHandler cargo = PackageEndpointAccess.consistCargo(live.get().level, currentDock);
        Map<String, Integer> cargoAddresses = PackageEndpointAccess.cargoAddresses(cargo);
        DeliveryCandidate delivery = bestDelivery(data, network, courier, currentStation, cargoAddresses);
        if (delivery != null) {
            if (!head.applyDispatchRoute(delivery.plan)) return;
            if (!head.getDispatchNavigation().setTarget(CONTROLLER_ID, delivery.destination.stationId, true)) {
                head.clearDispatchRoute();
                return;
            }
            CourierNetworkData.Job job = data.createDeliveryJob(courier.vehicleId, delivery.destination.id,
                    delivery.address, delivery.count, gameTime);
            data.setStatus(courier, CourierNetworkData.CourierStatus.TO_REQUESTER);
            head.setEngineOn(true);
            data.changed();
            CreateLittleNetworks.LOGGER.debug("Dispatched courier {} to deliver {} packages addressed to {} (job {})",
                    courier.vehicleId, delivery.count, delivery.address, job.id);
            return;
        }
        // Match Create's delivery instruction: packages without a reachable address block
        // retrieval instead of allowing the courier to accumulate more undeliverable cargo.
        if (!cargoAddresses.isEmpty()) {
            data.setStatus(courier, CourierNetworkData.CourierStatus.BLOCKED_NO_DESTINATION);
            return;
        }

        DispatchCandidate best = null;
        for (CourierNetworkData.Endpoint source : data.endpoints()) {
            if (!source.dimension.equals(courier.dimension) || source.outbound.isEmpty()
                    || network.stationNode(source.stationId).isEmpty()) continue;
            for (var demand : source.outbound.entrySet()) {
                int available = demand.getValue() - data.reserved(source.id, demand.getKey());
                if (available <= 0 || !matchesFilter(demand.getKey(), courier.addressFilter)) continue;
                for (CourierNetworkData.Endpoint destination : data.endpoints()) {
                    if (!destination.dimension.equals(courier.dimension)
                            || destination.stationId.equals(source.stationId)
                            || network.stationNode(destination.stationId).isEmpty()
                            || !PackageItem.matchAddress(demand.getKey(), destination.address)) continue;

                    Optional<DispatchRoutePlan> plan = network.closedPlan(List.of(
                            currentStation, source.stationId, destination.stationId, currentStation));
                    if (plan.isEmpty()) continue;
                    double cost = routeCost(network, currentStation, source.stationId, destination.stationId);
                    if (courier.mode == CourierNetworkData.CourierMode.DEPOT) {
                        cost += routeCost(network, destination.stationId, courier.homeStationId);
                    }
                    DispatchCandidate candidate = new DispatchCandidate(source, destination,
                            demand.getKey(), available, cost, source.demandSince, plan.get());
                    if (best == null || candidate.compareTo(best) < 0) best = candidate;
                }
            }
        }
        if (best == null) {
            data.setStatus(courier, CourierNetworkData.CourierStatus.WAITING_FOR_PACKAGES);
            return;
        }

        if (!head.applyDispatchRoute(best.plan)) return;
        if (!head.getDispatchNavigation().setTarget(CONTROLLER_ID, best.source.stationId, true)) {
            head.clearDispatchRoute();
            return;
        }
        Map<String, Integer> manifest = courier.mode == CourierNetworkData.CourierMode.DEPOT
                ? Map.of(best.address, best.available)
                : pickupManifest(data, network, courier, best.source);
        CourierNetworkData.Job job = data.createJob(courier.vehicleId, best.source.id,
                best.destination.id, best.address, manifest, gameTime);
        data.setStatus(courier, CourierNetworkData.CourierStatus.TO_PROVIDER);
        head.setEngineOn(true);
        head.getOwnerUUID().ifPresent(owner -> VehicleRegistrationData.get(server).register(live.get().entity, owner));
        data.changed();
        CreateLittleNetworks.LOGGER.debug("Dispatched courier {} for {} packages addressed to {} (job {})",
                courier.vehicleId, best.available, best.address, job.id);
    }

    public static void processDock(ServerLevel level, DockingStationBlockEntity dock, DispatchableHeadVehicle head) {
        UUID stationId = dock.getStationId();
        if (stationId == null || !(head instanceof Entity entity)) return;
        CourierNetworkData data = CourierNetworkData.get(level.getServer());
        Optional<CourierNetworkData.Courier> courierOptional = data.courier(entity.getUUID());
        if (courierOptional.isEmpty()) return;
        CourierNetworkData.Courier courier = courierOptional.get();
        if (!stationId.equals(courier.currentStationId)) {
            courier.currentStationId = stationId;
            data.changed();
        }
        Optional<CourierNetworkData.Job> jobOptional = data.jobForVehicle(entity.getUUID());
        if (jobOptional.isEmpty()) {
            head.setEngineOn(false);
            head.getDispatchNavigation().setTarget(CONTROLLER_ID, stationId, true);
            return;
        }

        CourierNetworkData.Job job = jobOptional.get();
        long now = level.getGameTime();
        IItemHandler cargo = PackageEndpointAccess.consistCargo(level, dock);
        CourierNetworkData.Endpoint source = data.endpoint(job.sourceEndpointId).orElse(null);
        CourierNetworkData.Endpoint destination = data.endpoint(job.destinationEndpointId).orElse(null);

        if (job.state == CourierNetworkData.JobState.RECOVERY_HOLD && destination != null
                && !destination.stationId.equals(stationId)) {
            beginDelivery(head, job, destination, courier, data, now);
            data.changed();
            return;
        }

        if ((job.state == CourierNetworkData.JobState.TO_PROVIDER
                || job.state == CourierNetworkData.JobState.LOADING)
                && source != null && source.stationId.equals(stationId)) {
            boolean justArrived = job.state != CourierNetworkData.JobState.LOADING;
            job.state = CourierNetworkData.JobState.LOADING;
            data.setStatus(courier, CourierNetworkData.CourierStatus.LOADING);
            if (justArrived) {
                job.stateSince = now;
                job.lastProgress = now;
            }
            boolean loadedAny = false;
            List<String> loadOrder = new java.util.ArrayList<>();
            if (job.reservedManifest.containsKey(job.address)) loadOrder.add(job.address);
            job.reservedManifest.keySet().stream().filter(address -> !address.equals(job.address))
                    .sorted().forEach(loadOrder::add);
            for (String address : loadOrder) {
                int target = job.reservedManifest.getOrDefault(address, 0);
                int loadedForAddress = job.loadedManifest.getOrDefault(address, 0);
                while (loadedForAddress < target && PackageEndpointAccess.loadOne(level, source, cargo, address)) {
                    loadedForAddress++;
                    job.loaded++;
                    job.loadedManifest.put(address, loadedForAddress);
                    job.lastProgress = now;
                    dock.notifyTransfer();
                    loadedAny = true;
                }
            }
            if (loadedAny) data.changed();
            Map<String, Integer> liveOutbound = PackageEndpointAccess.observeOutbound(level, source);
            data.observe(source, liveOutbound, now);
            boolean sourceStillHasPackage = job.reservedManifest.entrySet().stream().anyMatch(entry ->
                    job.loadedManifest.getOrDefault(entry.getKey(), 0) < entry.getValue()
                            && liveOutbound.getOrDefault(entry.getKey(), 0) > 0);
            if (job.loaded == 0 && !sourceStillHasPackage) {
                finishOrReturn(head, job, courier, stationId, data, now);
            } else if (job.loaded > 0 && now - job.lastProgress >= courier.pickupDelay) {
                if (PackageEndpointAccess.cargoContains(cargo, job.address)) {
                    beginDelivery(head, job, destination, courier, data, now);
                } else {
                    finishOrReturn(head, job, courier, stationId, data, now);
                }
            } else if (now - job.lastProgress >= CourierConfig.SERVER.transferTimeoutTicks.get()) {
                if (job.loaded > 0 && PackageEndpointAccess.cargoContains(cargo, job.address)) {
                    beginDelivery(head, job, destination, courier, data, now);
                } else {
                    finishOrReturn(head, job, courier, stationId, data, now);
                }
            }
            data.changed();
            return;
        }

        if ((job.state == CourierNetworkData.JobState.TO_REQUESTER
                || job.state == CourierNetworkData.JobState.UNLOADING
                || job.state == CourierNetworkData.JobState.RECOVERY_HOLD)
                && destination != null && destination.stationId.equals(stationId)) {
            boolean justArrived = job.state == CourierNetworkData.JobState.TO_REQUESTER;
            job.state = CourierNetworkData.JobState.UNLOADING;
            data.setStatus(courier, CourierNetworkData.CourierStatus.UNLOADING);
            if (justArrived) {
                job.stateSince = now;
                job.lastProgress = now;
            }
            if (PackageEndpointAccess.unloadOne(level, destination, cargo)) {
                job.lastProgress = now;
                dock.notifyTransfer();
                data.changed();
                return;
            }
            if (!PackageEndpointAccess.cargoContains(cargo, job.address)
                    && now - job.lastProgress >= courier.deliveryDelay) {
                finishOrReturn(head, job, courier, stationId, data, now);
            } else if (PackageEndpointAccess.cargoContains(cargo, job.address)
                    && now - job.lastProgress >= CourierConfig.SERVER.transferTimeoutTicks.get()) {
                job.state = CourierNetworkData.JobState.RECOVERY_HOLD;
                job.lastProgress = now;
                data.setStatus(courier, CourierNetworkData.CourierStatus.RECOVERY_HOLD);
                data.changed();
            }
            return;
        }

        if (job.state == CourierNetworkData.JobState.RETURNING
                && head.getDispatchNavigation().targetStationId().filter(stationId::equals).isPresent()) {
            head.setEngineOn(false);
            head.clearDispatchRoute();
            head.getDispatchNavigation().setTarget(CONTROLLER_ID, stationId, true);
            data.removeJob(job.id);
            data.setStatus(courier, CourierNetworkData.CourierStatus.WAITING_FOR_PACKAGES);
        }
    }

    private static void beginReturn(DispatchableHeadVehicle head, CourierNetworkData.Job job,
                                    CourierNetworkData.Courier courier, CourierNetworkData data, long now) {
        job.state = CourierNetworkData.JobState.RETURNING;
        data.setStatus(courier, CourierNetworkData.CourierStatus.RETURNING);
        job.stateSince = now;
        job.lastProgress = now;
        head.getDispatchNavigation().setTarget(CONTROLLER_ID, courier.homeStationId, true);
        head.setEngineOn(true);
    }

    private static void finishOrReturn(DispatchableHeadVehicle head, CourierNetworkData.Job job,
                                       CourierNetworkData.Courier courier, UUID stationId,
                                       CourierNetworkData data, long now) {
        if (courier.mode == CourierNetworkData.CourierMode.DEPOT) {
            beginReturn(head, job, courier, data, now);
            data.changed();
            return;
        }
        head.setEngineOn(false);
        head.clearDispatchRoute();
        head.getDispatchNavigation().setTarget(CONTROLLER_ID, stationId, true);
        data.removeJob(job.id);
    }

    private static void beginDelivery(DispatchableHeadVehicle head, CourierNetworkData.Job job,
                                      CourierNetworkData.Endpoint destination,
                                      CourierNetworkData.Courier courier, CourierNetworkData data, long now) {
        job.stateSince = now;
        job.lastProgress = now;
        if (destination == null) {
            job.state = CourierNetworkData.JobState.RECOVERY_HOLD;
            data.setStatus(courier, CourierNetworkData.CourierStatus.RECOVERY_HOLD);
            head.setEngineOn(false);
            return;
        }
        job.state = CourierNetworkData.JobState.TO_REQUESTER;
        data.setStatus(courier, CourierNetworkData.CourierStatus.TO_REQUESTER);
        head.getDispatchNavigation().setTarget(CONTROLLER_ID, destination.stationId, true);
        head.setEngineOn(true);
    }

    private static void recoverTimedOutJobs(MinecraftServer server, CourierNetworkData data, long gameTime) {
        for (CourierNetworkData.Job job : data.jobs()) {
            if (gameTime - job.lastProgress < CourierConfig.SERVER.transitTimeoutTicks.get()) continue;
            CourierNetworkData.Courier courier = data.courier(job.vehicleId).orElse(null);
            if (courier == null) {
                data.removeJob(job.id);
                continue;
            }
            Optional<LiveCourier> live = liveCourier(server, courier);
            if (live.isEmpty()) {
                if (!VehicleRegistrationData.get(server).isRegistered(job.vehicleId)) {
                    data.removeCourier(job.vehicleId);
                }
                continue;
            }
            if (job.loaded <= 0) {
                UUID recoveryStation = courier.mode == CourierNetworkData.CourierMode.DEPOT
                        ? courier.homeStationId : courier.currentStationId;
                job.state = CourierNetworkData.JobState.RETURNING;
                job.stateSince = gameTime;
                live.get().head.getDispatchNavigation().setTarget(CONTROLLER_ID, recoveryStation, true);
                live.get().head.setEngineOn(true);
                data.setStatus(courier, CourierNetworkData.CourierStatus.RETURNING);
            } else {
                job.state = CourierNetworkData.JobState.RECOVERY_HOLD;
                data.setStatus(courier, CourierNetworkData.CourierStatus.RECOVERY_HOLD);
                CourierNetworkData.Endpoint destination = data.endpoint(job.destinationEndpointId).orElse(null);
                if (destination == null) {
                    live.get().head.setEngineOn(false);
                } else {
                    live.get().head.getDispatchNavigation().setTarget(
                            CONTROLLER_ID, destination.stationId, true);
                    live.get().head.setEngineOn(true);
                }
            }
            job.lastProgress = gameTime;
            data.changed();
        }
    }

    private static Optional<LiveCourier> liveCourier(MinecraftServer server, CourierNetworkData.Courier courier) {
        ResourceLocation dimension = ResourceLocation.tryParse(courier.dimension);
        if (dimension == null) return Optional.empty();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return Optional.empty();
        Entity entity = level.getEntity(courier.vehicleId);
        return entity instanceof DispatchableHeadVehicle head
                ? Optional.of(new LiveCourier(level, entity, head)) : Optional.empty();
    }

    private static void pruneInvalidEndpoints(MinecraftServer server, CourierNetworkData data) {
        for (CourierNetworkData.Endpoint endpoint : data.endpoints()) {
            ResourceLocation dimension = ResourceLocation.tryParse(endpoint.dimension);
            if (dimension == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || !level.isLoaded(endpoint.portPos) || !level.isLoaded(endpoint.dockPos)) continue;
            BlockEntity dockEntity = level.getBlockEntity(endpoint.dockPos);
            boolean validDock = dockEntity instanceof DockingStationBlockEntity dock
                    && endpoint.stationId.equals(dock.getStationId());
            BlockEntity portEntity = level.getBlockEntity(endpoint.portPos);
            boolean validPort = endpoint.kind == CourierNetworkData.EndpointKind.POSTBOX
                    ? portEntity instanceof PostboxBlockEntity postbox
                    && postbox.target instanceof LittleLogisticsPackagePortTarget target
                    && target.targetPosition(endpoint.portPos).equals(endpoint.dockPos)
                    : portEntity instanceof PackagerBlockEntity packager
                    && DockPackageRouter.targetPosition(packager).equals(endpoint.dockPos);
            if (!validDock || !validPort) {
                data.removeEndpoint(endpoint.dimension, endpoint.portPos, endpoint.kind);
            }
        }
    }

    private static DockingStationBlockEntity currentDock(ServerLevel level, UUID stationId, UUID vehicleId) {
        var snapshot = dev.murad.shipping.block.dockingstation.DockingStationRegistry.getOrCreate(level)
                .snapshot(stationId).orElse(null);
        if (snapshot == null) return null;
        for (BlockPos pos : snapshot.members()) {
            if (!(level.getBlockEntity(pos) instanceof DockingStationBlockEntity dock)) continue;
            if (!(dock.getDockedHeadVehicle() instanceof Entity headEntity)) continue;
            if (headEntity.getUUID().equals(vehicleId)) return dock;
        }
        return null;
    }

    private static DeliveryCandidate bestDelivery(CourierNetworkData data, TransportNetwork network,
                                                   CourierNetworkData.Courier courier, UUID currentStation,
                                                   Map<String, Integer> cargo) {
        DeliveryCandidate best = null;
        for (var packages : cargo.entrySet()) {
            for (CourierNetworkData.Endpoint destination : data.endpoints()) {
                if (!destination.dimension.equals(courier.dimension)
                        || destination.stationId.equals(currentStation)
                        || network.stationNode(destination.stationId).isEmpty()
                        || !PackageItem.matchAddress(packages.getKey(), destination.address)) continue;
                Optional<DispatchRoutePlan> plan = network.closedPlan(
                        List.of(currentStation, destination.stationId, currentStation));
                if (plan.isEmpty()) continue;
                double cost = routeCost(network, currentStation, destination.stationId);
                DeliveryCandidate candidate = new DeliveryCandidate(destination, packages.getKey(),
                        packages.getValue(), cost, plan.get());
                if (best == null || candidate.compareTo(best) < 0) best = candidate;
            }
        }
        return best;
    }

    private static Map<String, Integer> pickupManifest(CourierNetworkData data, TransportNetwork network,
                                                       CourierNetworkData.Courier courier,
                                                       CourierNetworkData.Endpoint source) {
        Map<String, Integer> manifest = new LinkedHashMap<>();
        source.outbound.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            int available = entry.getValue() - data.reserved(source.id, entry.getKey());
            if (available <= 0 || !matchesFilter(entry.getKey(), courier.addressFilter)) return;
            boolean reachableDestination = data.endpoints().stream().anyMatch(destination ->
                    destination.dimension.equals(courier.dimension)
                            && !destination.stationId.equals(source.stationId)
                            && PackageItem.matchAddress(entry.getKey(), destination.address)
                            && routeCost(network, source.stationId, destination.stationId)
                            < Double.POSITIVE_INFINITY);
            if (reachableDestination) manifest.put(entry.getKey(), available);
        });
        return manifest;
    }

    private static boolean matchesFilter(String address, String filter) {
        try {
            String value = filter == null || filter.isBlank() ? "*" : filter;
            return address.matches(Glob.toRegexPattern(value, ""));
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static double routeCost(TransportNetwork network, UUID... stationIds) {
        double total = 0;
        for (int i = 0; i < stationIds.length - 1; i++) {
            var from = network.stationNode(stationIds[i]);
            var to = network.stationNode(stationIds[i + 1]);
            if (from.isEmpty() || to.isEmpty()) return Double.POSITIVE_INFINITY;
            var path = network.shortestPath(from.get().id(), to.get().id());
            if (path.isEmpty()) return Double.POSITIVE_INFINITY;
            total += path.get().cost();
        }
        return total;
    }

    private record LiveCourier(ServerLevel level, Entity entity, DispatchableHeadVehicle head) {}

    private record DeliveryCandidate(CourierNetworkData.Endpoint destination, String address,
                                     int count, double cost, DispatchRoutePlan plan)
            implements Comparable<DeliveryCandidate> {
        @Override
        public int compareTo(DeliveryCandidate other) {
            int costResult = Double.compare(cost, other.cost);
            if (costResult != 0) return costResult;
            int addressResult = address.compareTo(other.address);
            return addressResult != 0 ? addressResult
                    : destination.id.toString().compareTo(other.destination.id.toString());
        }
    }

    private record DispatchCandidate(CourierNetworkData.Endpoint source,
                                     CourierNetworkData.Endpoint destination,
                                     String address, int available, double cost,
                                     long demandSince, DispatchRoutePlan plan)
            implements Comparable<DispatchCandidate> {
        @Override
        public int compareTo(DispatchCandidate other) {
            int costResult = Double.compare(cost, other.cost);
            if (costResult != 0) return costResult;
            int ageResult = Long.compare(demandSince, other.demandSince);
            if (ageResult != 0) return ageResult;
            int sourceResult = source.id.toString().compareTo(other.source.id.toString());
            return sourceResult != 0 ? sourceResult : destination.id.toString().compareTo(other.destination.id.toString());
        }
    }
}
