package com.sanhiruzu.createlittlenetworks.routing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent endpoint directory, courier enrollment, counted reservations, and active jobs. */
public final class CourierNetworkData extends SavedData {
    public static final String NAME = "create_little_networks_dispatch";

    public enum EndpointKind { PACKAGER, POSTBOX }
    public enum CourierMode { CREATE_COMPATIBLE, DEPOT }
    public enum CourierStatus {
        WAITING_FOR_PACKAGES, TO_PROVIDER, LOADING, TO_REQUESTER, UNLOADING,
        RETURNING, BLOCKED_NO_DESTINATION, RECOVERY_HOLD, WAITING_FOR_DOCK, MISSING_NETWORK
    }
    public enum JobState { TO_PROVIDER, LOADING, TO_REQUESTER, UNLOADING, RETURNING, RECOVERY_HOLD }

    public static final class Endpoint {
        public final UUID id;
        public String dimension;
        public BlockPos dockPos;
        public BlockPos portPos;
        public UUID stationId;
        public EndpointKind kind;
        public String address;
        public long lastSeen;
        public long demandSince;
        public final Map<String, Integer> outbound = new LinkedHashMap<>();

        Endpoint(UUID id) { this.id = id; }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("dimension", dimension);
            tag.putLong("dock", dockPos.asLong());
            tag.putLong("port", portPos.asLong());
            tag.putUUID("station", stationId);
            tag.putString("kind", kind.name());
            tag.putString("address", address);
            tag.putLong("lastSeen", lastSeen);
            tag.putLong("demandSince", demandSince);
            ListTag demands = new ListTag();
            outbound.forEach((key, value) -> {
                CompoundTag demand = new CompoundTag();
                demand.putString("address", key);
                demand.putInt("count", value);
                demands.add(demand);
            });
            tag.put("outbound", demands);
            return tag;
        }

        static Endpoint load(CompoundTag tag) {
            Endpoint endpoint = new Endpoint(tag.getUUID("id"));
            endpoint.dimension = tag.getString("dimension");
            endpoint.dockPos = BlockPos.of(tag.getLong("dock"));
            endpoint.portPos = BlockPos.of(tag.getLong("port"));
            endpoint.stationId = tag.getUUID("station");
            endpoint.kind = EndpointKind.valueOf(tag.getString("kind"));
            endpoint.address = tag.getString("address");
            endpoint.lastSeen = tag.getLong("lastSeen");
            endpoint.demandSince = tag.getLong("demandSince");
            ListTag demands = tag.getList("outbound", Tag.TAG_COMPOUND);
            for (int i = 0; i < demands.size(); i++) {
                CompoundTag demand = demands.getCompound(i);
                endpoint.outbound.put(demand.getString("address"), demand.getInt("count"));
            }
            return endpoint;
        }
    }

    public static final class Courier {
        public final UUID vehicleId;
        public String dimension;
        public UUID networkId;
        public UUID homeStationId;
        public UUID currentStationId;
        public CourierMode mode;
        public CourierStatus status;
        public String addressFilter;
        public CompoundTag schedule;
        public int pickupDelay;
        public int deliveryDelay;

        Courier(UUID vehicleId) { this.vehicleId = vehicleId; }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("vehicle", vehicleId);
            tag.putString("dimension", dimension);
            tag.putUUID("network", networkId);
            tag.putUUID("home", homeStationId);
            tag.putUUID("currentStation", currentStationId);
            tag.putString("mode", mode.name());
            tag.putString("status", status.name());
            tag.putString("filter", addressFilter);
            tag.put("schedule", schedule.copy());
            tag.putInt("pickupDelay", pickupDelay);
            tag.putInt("deliveryDelay", deliveryDelay);
            return tag;
        }

        static Courier load(CompoundTag tag) {
            Courier courier = new Courier(tag.getUUID("vehicle"));
            courier.dimension = tag.getString("dimension");
            courier.networkId = tag.getUUID("network");
            courier.homeStationId = tag.getUUID("home");
            courier.currentStationId = tag.hasUUID("currentStation")
                    ? tag.getUUID("currentStation") : courier.homeStationId;
            try {
                courier.mode = tag.contains("mode")
                        ? CourierMode.valueOf(tag.getString("mode")) : CourierMode.DEPOT;
            } catch (IllegalArgumentException ignored) {
                courier.mode = CourierMode.DEPOT;
            }
            try {
                courier.status = tag.contains("status")
                        ? CourierStatus.valueOf(tag.getString("status")) : CourierStatus.WAITING_FOR_PACKAGES;
            } catch (IllegalArgumentException ignored) {
                courier.status = CourierStatus.WAITING_FOR_PACKAGES;
            }
            courier.addressFilter = tag.getString("filter");
            courier.schedule = tag.getCompound("schedule").copy();
            courier.pickupDelay = tag.getInt("pickupDelay");
            courier.deliveryDelay = tag.getInt("deliveryDelay");
            return courier;
        }
    }

    public static final class Job {
        public final UUID id;
        public UUID vehicleId;
        public UUID sourceEndpointId;
        public UUID destinationEndpointId;
        public String address;
        public int reserved;
        public int loaded;
        public final Map<String, Integer> reservedManifest = new LinkedHashMap<>();
        public final Map<String, Integer> loadedManifest = new LinkedHashMap<>();
        public JobState state;
        public long createdAt;
        public long stateSince;
        public long lastProgress;

        Job(UUID id) { this.id = id; }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("vehicle", vehicleId);
            tag.putUUID("source", sourceEndpointId);
            tag.putUUID("destination", destinationEndpointId);
            tag.putString("address", address);
            tag.putInt("reserved", reserved);
            tag.putInt("loaded", loaded);
            tag.put("reservedManifest", saveCounts(reservedManifest));
            tag.put("loadedManifest", saveCounts(loadedManifest));
            tag.putString("state", state.name());
            tag.putLong("createdAt", createdAt);
            tag.putLong("stateSince", stateSince);
            tag.putLong("lastProgress", lastProgress);
            return tag;
        }

        static Job load(CompoundTag tag) {
            Job job = new Job(tag.getUUID("id"));
            job.vehicleId = tag.getUUID("vehicle");
            job.sourceEndpointId = tag.getUUID("source");
            job.destinationEndpointId = tag.getUUID("destination");
            job.address = tag.getString("address");
            job.reserved = tag.getInt("reserved");
            job.loaded = tag.getInt("loaded");
            loadCounts(tag.getList("reservedManifest", Tag.TAG_COMPOUND), job.reservedManifest);
            loadCounts(tag.getList("loadedManifest", Tag.TAG_COMPOUND), job.loadedManifest);
            if (job.reservedManifest.isEmpty() && job.reserved > 0) {
                job.reservedManifest.put(job.address, job.reserved);
            }
            job.state = JobState.valueOf(tag.getString("state"));
            job.createdAt = tag.getLong("createdAt");
            job.stateSince = tag.getLong("stateSince");
            job.lastProgress = tag.getLong("lastProgress");
            return job;
        }

        private static ListTag saveCounts(Map<String, Integer> counts) {
            ListTag list = new ListTag();
            counts.forEach((address, count) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("address", address);
                entry.putInt("count", count);
                list.add(entry);
            });
            return list;
        }

        private static void loadCounts(ListTag list, Map<String, Integer> output) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int count = entry.getInt("count");
                if (count > 0) output.put(entry.getString("address"), count);
            }
        }
    }

    private final Map<UUID, Endpoint> endpoints = new LinkedHashMap<>();
    private final Map<UUID, Courier> couriers = new LinkedHashMap<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    transient long lastDispatchTick = Long.MIN_VALUE;

    public static CourierNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CourierNetworkData::new, CourierNetworkData::load, null), NAME);
    }

    public Endpoint registerEndpoint(String dimension, BlockPos dockPos, BlockPos portPos, UUID stationId,
                                     EndpointKind kind, String address, long gameTime) {
        String key = dimension + '|' + portPos.asLong() + '|' + kind.name();
        UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        Endpoint endpoint = endpoints.computeIfAbsent(id, Endpoint::new);
        endpoint.dimension = dimension;
        endpoint.dockPos = dockPos.immutable();
        endpoint.portPos = portPos.immutable();
        endpoint.stationId = stationId;
        endpoint.kind = kind;
        endpoint.address = address == null ? "" : address;
        endpoint.lastSeen = gameTime;
        setDirty();
        return endpoint;
    }

    public void removeEndpoint(String dimension, BlockPos portPos, EndpointKind kind) {
        String key = dimension + '|' + portPos.asLong() + '|' + kind.name();
        if (endpoints.remove(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))) != null) setDirty();
    }

    public List<Endpoint> endpointsAtDock(String dimension, BlockPos dockPos) {
        return endpoints.values().stream().filter(endpoint -> endpoint.dimension.equals(dimension)
                && endpoint.dockPos.equals(dockPos)).sorted(Comparator.comparing(endpoint -> endpoint.id.toString())).toList();
    }

    public List<Endpoint> endpointsAtStation(String dimension, UUID stationId) {
        return endpoints.values().stream().filter(endpoint -> endpoint.dimension.equals(dimension)
                && endpoint.stationId.equals(stationId))
                .sorted(Comparator.comparing(endpoint -> endpoint.id.toString())).toList();
    }

    public List<Endpoint> endpoints() { return List.copyOf(endpoints.values()); }
    public Optional<Endpoint> endpoint(UUID id) { return Optional.ofNullable(endpoints.get(id)); }

    public void observe(Endpoint endpoint, Map<String, Integer> outbound, long gameTime) {
        if (!outbound.equals(endpoint.outbound)) {
            if (endpoint.outbound.isEmpty() && !outbound.isEmpty()) endpoint.demandSince = gameTime;
            endpoint.outbound.clear();
            endpoint.outbound.putAll(outbound);
            setDirty();
        }
        endpoint.lastSeen = gameTime;
    }

    public Courier enroll(UUID vehicleId, String dimension, UUID networkId, UUID homeStationId,
                          UUID currentStationId, CourierMode mode, String filter, CompoundTag schedule,
                          int pickupDelay, int deliveryDelay) {
        Courier courier = new Courier(vehicleId);
        courier.dimension = dimension;
        courier.networkId = networkId;
        courier.homeStationId = homeStationId;
        courier.currentStationId = currentStationId;
        courier.mode = mode;
        courier.status = CourierStatus.WAITING_FOR_PACKAGES;
        courier.addressFilter = filter;
        courier.schedule = schedule.copy();
        courier.pickupDelay = pickupDelay;
        courier.deliveryDelay = deliveryDelay;
        couriers.put(vehicleId, courier);
        setDirty();
        return courier;
    }

    public Optional<Courier> courier(UUID vehicle) { return Optional.ofNullable(couriers.get(vehicle)); }
    public List<Courier> couriers() { return List.copyOf(couriers.values()); }
    public boolean isCourier(UUID vehicle) { return couriers.containsKey(vehicle); }

    public void setStatus(Courier courier, CourierStatus status) {
        if (courier.status == status) return;
        courier.status = status;
        setDirty();
    }

    public Optional<Courier> removeCourier(UUID vehicle) {
        jobs.values().removeIf(job -> job.vehicleId.equals(vehicle));
        Courier removed = couriers.remove(vehicle);
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    public Optional<Job> jobForVehicle(UUID vehicle) {
        return jobs.values().stream().filter(job -> job.vehicleId.equals(vehicle)).findFirst();
    }

    public List<Job> jobs() { return List.copyOf(jobs.values()); }

    public Job createJob(UUID vehicle, UUID source, UUID destination, String address,
                         int reserved, long gameTime) {
        return createJob(vehicle, source, destination, address,
                reserved > 0 ? Map.of(address, reserved) : Map.of(), gameTime);
    }

    public Job createJob(UUID vehicle, UUID source, UUID destination, String address,
                         Map<String, Integer> manifest, long gameTime) {
        Job job = new Job(UUID.randomUUID());
        job.vehicleId = vehicle;
        job.sourceEndpointId = source;
        job.destinationEndpointId = destination;
        job.address = address;
        manifest.forEach((key, value) -> {
            if (value > 0) job.reservedManifest.put(key, value);
        });
        job.reserved = job.reservedManifest.values().stream().mapToInt(Integer::intValue).sum();
        job.state = JobState.TO_PROVIDER;
        job.createdAt = gameTime;
        job.stateSince = gameTime;
        job.lastProgress = gameTime;
        jobs.put(job.id, job);
        setDirty();
        return job;
    }

    public Job createDeliveryJob(UUID vehicle, UUID destination, String address,
                                 int loaded, long gameTime) {
        Job job = createJob(vehicle, destination, destination, address, 0, gameTime);
        job.loaded = loaded;
        if (loaded > 0) job.loadedManifest.put(address, loaded);
        job.state = JobState.TO_REQUESTER;
        setDirty();
        return job;
    }

    public int reserved(UUID endpointId, String address) {
        return jobs.values().stream().filter(job -> job.sourceEndpointId.equals(endpointId)
                        && (job.state == JobState.TO_PROVIDER || job.state == JobState.LOADING))
                .mapToInt(job -> job.reservedManifest.getOrDefault(address, 0)).sum();
    }

    public void removeJob(UUID id) {
        if (jobs.remove(id) != null) setDirty();
    }

    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag endpointTags = new ListTag();
        endpoints.values().forEach(endpoint -> endpointTags.add(endpoint.save()));
        tag.put("endpoints", endpointTags);
        ListTag courierTags = new ListTag();
        couriers.values().forEach(courier -> courierTags.add(courier.save()));
        tag.put("couriers", courierTags);
        ListTag jobTags = new ListTag();
        jobs.values().forEach(job -> jobTags.add(job.save()));
        tag.put("jobs", jobTags);
        tag.putInt("version", 2);
        return tag;
    }

    public static CourierNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        CourierNetworkData data = new CourierNetworkData();
        ListTag endpointTags = tag.getList("endpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < endpointTags.size(); i++) {
            Endpoint endpoint = Endpoint.load(endpointTags.getCompound(i));
            data.endpoints.put(endpoint.id, endpoint);
        }
        ListTag courierTags = tag.getList("couriers", Tag.TAG_COMPOUND);
        for (int i = 0; i < courierTags.size(); i++) {
            Courier courier = Courier.load(courierTags.getCompound(i));
            data.couriers.put(courier.vehicleId, courier);
        }
        ListTag jobTags = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobTags.size(); i++) {
            Job job = Job.load(jobTags.getCompound(i));
            data.jobs.put(job.id, job);
        }
        return data;
    }
}
