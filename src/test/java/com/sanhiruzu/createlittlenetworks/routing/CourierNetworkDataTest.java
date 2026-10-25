package com.sanhiruzu.createlittlenetworks.routing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierNetworkDataTest {
    @Test
    void courierModeLocationAndStatusSurviveSaveReload() {
        CourierNetworkData data = new CourierNetworkData();
        UUID vehicle = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        CourierNetworkData.Courier courier = data.enroll(vehicle, "minecraft:overworld",
                UUID.randomUUID(), home, current, CourierNetworkData.CourierMode.CREATE_COMPATIBLE,
                "Factory *", new CompoundTag(), 20, 40);
        data.setStatus(courier, CourierNetworkData.CourierStatus.BLOCKED_NO_DESTINATION);

        CourierNetworkData decoded = CourierNetworkData.load(data.save(new CompoundTag(), null), null);
        CourierNetworkData.Courier restored = decoded.courier(vehicle).orElseThrow();
        assertEquals(CourierNetworkData.CourierMode.CREATE_COMPATIBLE, restored.mode);
        assertEquals(current, restored.currentStationId);
        assertEquals(CourierNetworkData.CourierStatus.BLOCKED_NO_DESTINATION, restored.status);
        assertEquals("Factory *", restored.addressFilter);
    }

    @Test
    void countedReservationsCannotOverbookObservedDemand() {
        CourierNetworkData data = new CourierNetworkData();
        UUID station = UUID.randomUUID();
        CourierNetworkData.Endpoint endpoint = data.registerEndpoint("minecraft:overworld",
                BlockPos.ZERO, BlockPos.ZERO.above(), station,
                CourierNetworkData.EndpointKind.PACKAGER, "Source", 10);
        data.observe(endpoint, Map.of("Factory A", 3), 10);
        UUID courier = UUID.randomUUID();
        data.enroll(courier, "minecraft:overworld", UUID.randomUUID(), station, station,
                CourierNetworkData.CourierMode.DEPOT, "*", new CompoundTag(), 0, 0);
        data.createJob(courier, endpoint.id, UUID.randomUUID(), "Factory A", 2, 10);

        assertEquals(2, data.reserved(endpoint.id, "Factory A"));
        assertEquals(1, endpoint.outbound.get("Factory A") - data.reserved(endpoint.id, "Factory A"));
    }

    @Test
    void multipleCouriersShareTheSameCountedReservationLedger() {
        CourierNetworkData data = new CourierNetworkData();
        UUID station = UUID.randomUUID();
        CourierNetworkData.Endpoint endpoint = data.registerEndpoint("minecraft:overworld",
                BlockPos.ZERO, BlockPos.ZERO.above(), station,
                CourierNetworkData.EndpointKind.PACKAGER, "Source", 10);
        data.observe(endpoint, Map.of("Factory A", 3), 10);

        UUID firstCourier = UUID.randomUUID();
        UUID secondCourier = UUID.randomUUID();
        data.createJob(firstCourier, endpoint.id, UUID.randomUUID(),
                "Factory A", Map.of("Factory A", 2), 10);

        int remaining = endpoint.outbound.get("Factory A") - data.reserved(endpoint.id, "Factory A");
        data.createJob(secondCourier, endpoint.id, UUID.randomUUID(),
                "Factory A", Map.of("Factory A", remaining), 10);

        assertEquals(3, data.reserved(endpoint.id, "Factory A"));
        assertEquals(0, endpoint.outbound.get("Factory A") - data.reserved(endpoint.id, "Factory A"));
    }

    @Test
    void multiAddressManifestReservesOnlyUntilPickupCompletes() {
        CourierNetworkData data = new CourierNetworkData();
        UUID station = UUID.randomUUID();
        CourierNetworkData.Endpoint endpoint = data.registerEndpoint("minecraft:overworld",
                BlockPos.ZERO, BlockPos.ZERO.above(), station,
                CourierNetworkData.EndpointKind.POSTBOX, "Source", 10);
        UUID vehicle = UUID.randomUUID();
        CourierNetworkData.Job job = data.createJob(vehicle, endpoint.id, UUID.randomUUID(),
                "Factory A", Map.of("Factory A", 2, "Factory B", 3), 10);
        job.loaded = 2;
        job.loadedManifest.put("Factory A", 2);

        assertEquals(2, data.reserved(endpoint.id, "Factory A"));
        assertEquals(3, data.reserved(endpoint.id, "Factory B"));

        CourierNetworkData decoded = CourierNetworkData.load(data.save(new CompoundTag(), null), null);
        CourierNetworkData.Job restored = decoded.jobForVehicle(vehicle).orElseThrow();
        assertEquals(Map.of("Factory A", 2, "Factory B", 3), restored.reservedManifest);
        assertEquals(Map.of("Factory A", 2), restored.loadedManifest);
        restored.state = CourierNetworkData.JobState.TO_REQUESTER;
        assertEquals(0, decoded.reserved(endpoint.id, "Factory A"));
        assertEquals(0, decoded.reserved(endpoint.id, "Factory B"));
    }
}
