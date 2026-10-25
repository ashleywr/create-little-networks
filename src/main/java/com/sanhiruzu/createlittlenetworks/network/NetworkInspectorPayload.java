package com.sanhiruzu.createlittlenetworks.network;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.routing.CourierNetworkData;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A bounded, read-only server snapshot used by the Network Inspector screen. */
public record NetworkInspectorPayload(UUID networkId, String name, String mode, long revision,
                                      long gameTime, List<NodeView> nodes, List<EdgeView> edges,
                                      List<EndpointView> endpoints, List<CourierView> couriers,
                                      List<JobView> jobs) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 4096;

    public static final Type<NetworkInspectorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateLittleNetworks.MOD_ID, "network_inspector"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkInspectorPayload> STREAM_CODEC =
            StreamCodec.of(NetworkInspectorPayload::encode, NetworkInspectorPayload::decode);

    public NetworkInspectorPayload {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        endpoints = List.copyOf(endpoints);
        couriers = List.copyOf(couriers);
        jobs = List.copyOf(jobs);
    }

    public record NodeView(UUID id, String name, String dimension, BlockPos position,
                           @Nullable UUID stationId) {}
    public record EdgeView(UUID from, UUID to, double cost) {}
    public record DemandView(String address, int available, int reserved) {}
    public record EndpointView(UUID id, UUID stationId, String kind, String address,
                               String dimension, BlockPos dockPos, BlockPos portPos,
                               List<DemandView> demand) {
        public EndpointView {
            demand = List.copyOf(demand);
        }
    }
    public record CourierView(UUID vehicleId, UUID homeStationId, UUID currentStationId,
                              String courierMode, String status, String dimension, String addressFilter,
                              int pickupDelay, int deliveryDelay) {}
    public record JobView(UUID id, UUID vehicleId, UUID sourceEndpointId, UUID destinationEndpointId,
                          String state, String address, int reserved, int loaded,
                          long createdAt, long lastProgress) {}

    public static NetworkInspectorPayload create(ServerPlayer player, TransportNetwork network) {
        List<NodeView> nodes = network.nodes().stream()
                .sorted(Comparator.comparing(TransportNode::name).thenComparing(node -> node.id().toString()))
                .limit(MAX_ENTRIES)
                .map(node -> new NodeView(node.id(), node.name(), node.dimension(), node.position(), node.stationId()))
                .toList();
        List<EdgeView> edges = network.edges().stream()
                .sorted(Comparator.comparing((dev.murad.shipping.transport.TransportEdge edge) -> edge.from().toString())
                        .thenComparing(edge -> edge.to().toString()))
                .limit(MAX_ENTRIES)
                .map(edge -> new EdgeView(edge.from(), edge.to(), edge.cost()))
                .toList();

        Set<UUID> stationIds = new HashSet<>();
        network.nodes().forEach(node -> node.station().ifPresent(stationIds::add));
        CourierNetworkData dispatch = CourierNetworkData.get(player.server);

        List<EndpointView> endpoints = dispatch.endpoints().stream()
                .filter(endpoint -> stationIds.contains(endpoint.stationId))
                .sorted(Comparator.comparing(endpoint -> endpoint.id.toString()))
                .limit(MAX_ENTRIES)
                .map(endpoint -> {
                    List<DemandView> demand = endpoint.outbound.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .limit(MAX_ENTRIES)
                            .map(entry -> new DemandView(entry.getKey(), entry.getValue(),
                                    dispatch.reserved(endpoint.id, entry.getKey())))
                            .toList();
                    return new EndpointView(endpoint.id, endpoint.stationId, endpoint.kind.name(), endpoint.address,
                            endpoint.dimension, endpoint.dockPos, endpoint.portPos, demand);
                }).toList();

        List<CourierView> couriers = dispatch.couriers().stream()
                .filter(courier -> network.id().equals(courier.networkId))
                .sorted(Comparator.comparing(courier -> courier.vehicleId.toString()))
                .limit(MAX_ENTRIES)
                .map(courier -> new CourierView(courier.vehicleId, courier.homeStationId,
                        courier.currentStationId, courier.mode.name(), courier.status.name(),
                        courier.dimension, courier.addressFilter, courier.pickupDelay, courier.deliveryDelay))
                .toList();
        Set<UUID> courierIds = new HashSet<>();
        couriers.forEach(courier -> courierIds.add(courier.vehicleId()));
        List<JobView> jobs = dispatch.jobs().stream()
                .filter(job -> courierIds.contains(job.vehicleId))
                .sorted(Comparator.comparing(job -> job.id.toString()))
                .limit(MAX_ENTRIES)
                .map(job -> new JobView(job.id, job.vehicleId, job.sourceEndpointId, job.destinationEndpointId,
                        job.state.name(), job.address, job.reserved, job.loaded, job.createdAt, job.lastProgress))
                .toList();

        return new NetworkInspectorPayload(network.id(), network.name(), network.mode().getSerializedName(),
                network.revision(), player.level().getGameTime(), nodes, edges, endpoints, couriers, jobs);
    }

    public static NetworkInspectorPayload empty(ServerPlayer player) {
        return new NetworkInspectorPayload(new UUID(0, 0), "No chart selected", "none", 0,
                player.level().getGameTime(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buf, NetworkInspectorPayload payload) {
        buf.writeUUID(payload.networkId);
        buf.writeUtf(payload.name, 256);
        buf.writeUtf(payload.mode, 32);
        buf.writeLong(payload.revision);
        buf.writeLong(payload.gameTime);

        buf.writeVarInt(payload.nodes.size());
        for (NodeView node : payload.nodes) {
            buf.writeUUID(node.id);
            buf.writeUtf(node.name, 256);
            buf.writeUtf(node.dimension, 256);
            buf.writeBlockPos(node.position);
            writeNullableUuid(buf, node.stationId);
        }
        buf.writeVarInt(payload.edges.size());
        for (EdgeView edge : payload.edges) {
            buf.writeUUID(edge.from);
            buf.writeUUID(edge.to);
            buf.writeDouble(edge.cost);
        }
        buf.writeVarInt(payload.endpoints.size());
        for (EndpointView endpoint : payload.endpoints) {
            buf.writeUUID(endpoint.id);
            buf.writeUUID(endpoint.stationId);
            buf.writeUtf(endpoint.kind, 32);
            buf.writeUtf(endpoint.address, 256);
            buf.writeUtf(endpoint.dimension, 256);
            buf.writeBlockPos(endpoint.dockPos);
            buf.writeBlockPos(endpoint.portPos);
            buf.writeVarInt(endpoint.demand.size());
            for (DemandView demand : endpoint.demand) {
                buf.writeUtf(demand.address, 256);
                buf.writeVarInt(demand.available);
                buf.writeVarInt(demand.reserved);
            }
        }
        buf.writeVarInt(payload.couriers.size());
        for (CourierView courier : payload.couriers) {
            buf.writeUUID(courier.vehicleId);
            buf.writeUUID(courier.homeStationId);
            buf.writeUUID(courier.currentStationId);
            buf.writeUtf(courier.courierMode, 32);
            buf.writeUtf(courier.status, 64);
            buf.writeUtf(courier.dimension, 256);
            buf.writeUtf(courier.addressFilter, 256);
            buf.writeVarInt(courier.pickupDelay);
            buf.writeVarInt(courier.deliveryDelay);
        }
        buf.writeVarInt(payload.jobs.size());
        for (JobView job : payload.jobs) {
            buf.writeUUID(job.id);
            buf.writeUUID(job.vehicleId);
            buf.writeUUID(job.sourceEndpointId);
            buf.writeUUID(job.destinationEndpointId);
            buf.writeUtf(job.state, 64);
            buf.writeUtf(job.address, 256);
            buf.writeVarInt(job.reserved);
            buf.writeVarInt(job.loaded);
            buf.writeLong(job.createdAt);
            buf.writeLong(job.lastProgress);
        }
    }

    private static NetworkInspectorPayload decode(RegistryFriendlyByteBuf buf) {
        UUID networkId = buf.readUUID();
        String name = buf.readUtf(256);
        String mode = buf.readUtf(32);
        long revision = buf.readLong();
        long gameTime = buf.readLong();

        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0, count = readCount(buf); i < count; i++) {
            nodes.add(new NodeView(buf.readUUID(), buf.readUtf(256), buf.readUtf(256),
                    buf.readBlockPos(), readNullableUuid(buf)));
        }
        List<EdgeView> edges = new ArrayList<>();
        for (int i = 0, count = readCount(buf); i < count; i++) {
            edges.add(new EdgeView(buf.readUUID(), buf.readUUID(), buf.readDouble()));
        }
        List<EndpointView> endpoints = new ArrayList<>();
        for (int i = 0, count = readCount(buf); i < count; i++) {
            UUID id = buf.readUUID();
            UUID stationId = buf.readUUID();
            String kind = buf.readUtf(32);
            String address = buf.readUtf(256);
            String dimension = buf.readUtf(256);
            BlockPos dockPos = buf.readBlockPos();
            BlockPos portPos = buf.readBlockPos();
            List<DemandView> demand = new ArrayList<>();
            for (int j = 0, demandCount = readCount(buf); j < demandCount; j++) {
                demand.add(new DemandView(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
            }
            endpoints.add(new EndpointView(id, stationId, kind, address, dimension, dockPos, portPos, demand));
        }
        List<CourierView> couriers = new ArrayList<>();
        for (int i = 0, count = readCount(buf); i < count; i++) {
            couriers.add(new CourierView(buf.readUUID(), buf.readUUID(), buf.readUUID(), buf.readUtf(32),
                    buf.readUtf(64), buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
        }
        List<JobView> jobs = new ArrayList<>();
        for (int i = 0, count = readCount(buf); i < count; i++) {
            jobs.add(new JobView(buf.readUUID(), buf.readUUID(), buf.readUUID(), buf.readUUID(),
                    buf.readUtf(64), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong(), buf.readLong()));
        }
        return new NetworkInspectorPayload(networkId, name, mode, revision, gameTime,
                nodes, edges, endpoints, couriers, jobs);
    }

    private static int readCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid inspector list size: " + count);
        return count;
    }

    private static void writeNullableUuid(RegistryFriendlyByteBuf buf, @Nullable UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeUUID(value);
    }

    private static @Nullable UUID readNullableUuid(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
