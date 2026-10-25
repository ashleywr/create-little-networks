package com.sanhiruzu.createlittlenetworks.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkInspectorPayloadTest {
    @Test
    void courierModeStatusAndLocationRoundTripOverNetwork() {
        UUID network = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        UUID station = UUID.randomUUID();
        UUID endpoint = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        NetworkInspectorPayload payload = new NetworkInspectorPayload(network, "Test Network", "rail", 7, 100,
                List.of(new NetworkInspectorPayload.NodeView(node, "Station", "minecraft:overworld",
                        new BlockPos(1, 2, 3), station)),
                List.of(),
                List.of(new NetworkInspectorPayload.EndpointView(endpoint, station, "POSTBOX", "Factory *",
                        "minecraft:overworld", new BlockPos(1, 2, 3), new BlockPos(2, 2, 3),
                        List.of(new NetworkInspectorPayload.DemandView("Factory A", 4, 2)))),
                List.of(new NetworkInspectorPayload.CourierView(vehicle, station, station,
                        "CREATE_COMPATIBLE", "BLOCKED_NO_DESTINATION", "minecraft:overworld",
                        "Factory *", 20, 40)),
                List.of(new NetworkInspectorPayload.JobView(job, vehicle, endpoint, endpoint,
                        "TO_REQUESTER", "Factory A", 4, 2, 90, 99)));

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        NetworkInspectorPayload.STREAM_CODEC.encode(buffer, payload);
        NetworkInspectorPayload decoded = NetworkInspectorPayload.STREAM_CODEC.decode(buffer);

        assertEquals(payload, decoded);
    }
}
