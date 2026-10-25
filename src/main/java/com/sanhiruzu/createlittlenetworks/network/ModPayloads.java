package com.sanhiruzu.createlittlenetworks.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModPayloads {
    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(NetworkInspectorPayload.TYPE, NetworkInspectorPayload.STREAM_CODEC,
                (payload, context) -> NetworkInspectorClientHandler.handle(payload));
        registrar.playToServer(PanelEditPayload.TYPE, PanelEditPayload.STREAM_CODEC, PanelEditPayload::handle);
    }
}
