package com.sanhiruzu.createlittlenetworks.panel;

import com.sanhiruzu.createlittlenetworks.routing.CourierNetworkData;
import dev.murad.shipping.transport.TransportEdge;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import dev.murad.shipping.transport.TransportNode;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PanelNetworkEvaluator {
    private PanelNetworkEvaluator() {}

    static boolean update(ServerLevel level, PanelDocument document) {
        if (!document.isNetworkBound()) return false;
        TransportNetwork network = TransportNetworkData.get(level.getServer()).get(document.networkId()).orElse(null);
        boolean changed = false;
        if (network == null) {
            for (PanelWidget widget : document.widgets()) {
                if (isNetworkWidget(widget) && widget.value() != 15) {
                    widget.setValue(15);
                    changed = true;
                }
            }
            return changed;
        }

        CourierNetworkData dispatch = CourierNetworkData.get(level.getServer());
        List<CourierNetworkData.Courier> couriers = dispatch.couriers().stream()
                .filter(courier -> network.id().equals(courier.networkId)).toList();
        Set<UUID> courierIds = couriers.stream().map(courier -> courier.vehicleId).collect(java.util.stream.Collectors.toSet());
        List<CourierNetworkData.Job> jobs = dispatch.jobs().stream()
                .filter(job -> courierIds.contains(job.vehicleId)).toList();
        Map<UUID, CourierNetworkData.Endpoint> endpoints = new HashMap<>();
        dispatch.endpoints().forEach(endpoint -> endpoints.put(endpoint.id, endpoint));
        Set<EdgeKey> activeEdges = activeEdges(network, jobs, endpoints);

        int totalDemand = dispatch.endpoints().stream()
                .filter(endpoint -> network.stationNode(endpoint.stationId).isPresent())
                .mapToInt(endpoint -> endpoint.outbound.values().stream().mapToInt(Integer::intValue).sum()).sum();
        int blocked = (int) couriers.stream().filter(courier -> courier.status.name().startsWith("BLOCKED")
                || courier.status == CourierNetworkData.CourierStatus.RECOVERY_HOLD
                || courier.status == CourierNetworkData.CourierStatus.MISSING_NETWORK).count();

        for (PanelWidget widget : document.widgets()) {
            int value = value(widget, network, dispatch, couriers, jobs, activeEdges, totalDemand, blocked);
            if (value >= 0 && widget.value() != value) {
                widget.setValue(value);
                changed = true;
            }
        }
        return changed;
    }

    private static int value(PanelWidget widget, TransportNetwork network, CourierNetworkData dispatch,
                             List<CourierNetworkData.Courier> couriers, List<CourierNetworkData.Job> jobs,
                             Set<EdgeKey> activeEdges, int totalDemand, int blocked) {
        PanelBinding binding = widget.binding();
        return switch (binding.kind()) {
            case NETWORK_NODE -> stationValue(binding.firstId(), network, dispatch, couriers, jobs);
            case NETWORK_EDGE -> activeEdges.contains(new EdgeKey(binding.firstId(), binding.secondId())) ? 15
                    : network.edges().stream().anyMatch(edge -> edge.from().equals(binding.firstId())
                    && edge.to().equals(binding.secondId())) ? 3 : 0;
            case COURIER -> couriers.stream().filter(courier -> courier.vehicleId.equals(binding.firstId()))
                    .findFirst().map(PanelNetworkEvaluator::courierValue).orElse(15);
            case NETWORK_METRIC -> switch (binding.metric()) {
                case TOTAL_DEMAND -> totalDemand;
                case ACTIVE_JOBS -> jobs.size();
                case ENROLLED_COURIERS -> couriers.size();
                case BLOCKED_COURIERS -> blocked;
                case STATION_DEMAND -> stationDemand(binding.firstId(), network, dispatch);
            };
            case NONE -> switch (widget.type()) {
                case NETWORK_SUMMARY -> jobs.size();
                case DEMAND_COUNTER -> totalDemand;
                default -> -1;
            };
            default -> -1;
        };
    }

    private static int stationValue(UUID nodeId, TransportNetwork network, CourierNetworkData dispatch,
                                    List<CourierNetworkData.Courier> couriers,
                                    List<CourierNetworkData.Job> jobs) {
        if (nodeId == null) return 0;
        TransportNode node = network.node(nodeId).orElse(null);
        if (node == null || node.station().isEmpty()) return 0;
        UUID station = node.station().get();
        boolean stationBlocked = couriers.stream().anyMatch(courier -> station.equals(courier.currentStationId)
                && courierValue(courier) == 15);
        if (stationBlocked) return 15;
        boolean jobTarget = jobs.stream().anyMatch(job -> endpointAtStation(dispatch, job.sourceEndpointId, station)
                || endpointAtStation(dispatch, job.destinationEndpointId, station));
        if (jobTarget) return 12;
        if (couriers.stream().anyMatch(courier -> station.equals(courier.currentStationId))) return 8;
        return stationDemand(nodeId, network, dispatch) > 0 ? 4 : 0;
    }

    private static int stationDemand(UUID nodeId, TransportNetwork network, CourierNetworkData dispatch) {
        if (nodeId == null) return 0;
        TransportNode node = network.node(nodeId).orElse(null);
        if (node == null || node.station().isEmpty()) return 0;
        return dispatch.endpointsAtStation(node.dimension(), node.station().get()).stream()
                .mapToInt(endpoint -> endpoint.outbound.values().stream().mapToInt(Integer::intValue).sum()).sum();
    }

    private static boolean endpointAtStation(CourierNetworkData dispatch, UUID endpointId, UUID stationId) {
        return dispatch.endpoint(endpointId).map(endpoint -> stationId.equals(endpoint.stationId)).orElse(false);
    }

    private static int courierValue(CourierNetworkData.Courier courier) {
        return switch (courier.status) {
            case BLOCKED_NO_DESTINATION, RECOVERY_HOLD, MISSING_NETWORK -> 15;
            case TO_PROVIDER, TO_REQUESTER, RETURNING -> 12;
            case LOADING, UNLOADING -> 8;
            case WAITING_FOR_DOCK -> 6;
            case WAITING_FOR_PACKAGES -> 2;
        };
    }

    private static Set<EdgeKey> activeEdges(TransportNetwork network, List<CourierNetworkData.Job> jobs,
                                            Map<UUID, CourierNetworkData.Endpoint> endpoints) {
        Set<EdgeKey> active = new HashSet<>();
        for (CourierNetworkData.Job job : jobs) {
            CourierNetworkData.Endpoint source = endpoints.get(job.sourceEndpointId);
            CourierNetworkData.Endpoint destination = endpoints.get(job.destinationEndpointId);
            if (source == null || destination == null) continue;
            TransportNode from = network.stationNode(source.stationId).orElse(null);
            TransportNode to = network.stationNode(destination.stationId).orElse(null);
            if (from == null || to == null) continue;
            network.shortestPath(from.id(), to.id()).ifPresent(path -> {
                for (TransportEdge edge : path.edges()) active.add(new EdgeKey(edge.from(), edge.to()));
            });
        }
        return active;
    }

    private static boolean isNetworkWidget(PanelWidget widget) {
        return widget.type().category() == PanelWidgetType.Category.NETWORK
                || widget.binding().kind() == PanelBinding.Kind.NETWORK_NODE
                || widget.binding().kind() == PanelBinding.Kind.NETWORK_EDGE
                || widget.binding().kind() == PanelBinding.Kind.COURIER
                || widget.binding().kind() == PanelBinding.Kind.NETWORK_METRIC;
    }

    private record EdgeKey(UUID from, UUID to) {}
}
