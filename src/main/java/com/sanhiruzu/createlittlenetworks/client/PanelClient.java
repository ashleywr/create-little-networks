package com.sanhiruzu.createlittlenetworks.client;

import com.sanhiruzu.createlittlenetworks.registry.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class PanelClient {
    private PanelClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PanelClient::setup);
        modBus.addListener(PanelClient::registerRenderers);
        modBus.addListener(PanelKeyMappings::register);
    }

    private static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(PanelKeyMappings::tick);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MIMIC_PANEL.get(), MimicPanelRenderer::new);
    }
}
