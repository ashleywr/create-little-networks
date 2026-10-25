package com.sanhiruzu.createlittlenetworks;

import com.mojang.logging.LogUtils;
import com.sanhiruzu.createlittlenetworks.registry.ModPackagePortTargetTypes;
import com.sanhiruzu.createlittlenetworks.registry.ModBlockEntities;
import com.sanhiruzu.createlittlenetworks.registry.ModBlocks;
import com.sanhiruzu.createlittlenetworks.registry.ModItems;
import com.sanhiruzu.createlittlenetworks.network.ModPayloads;
import com.sanhiruzu.createlittlenetworks.routing.CourierDockService;
import com.sanhiruzu.createlittlenetworks.schedule.LittleLogisticsDepotInstruction;
import com.simibubi.create.content.trains.schedule.Schedule;
import dev.murad.shipping.api.DockingStationServices;
import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

@Mod(CreateLittleNetworks.MOD_ID)
public final class CreateLittleNetworks {
    public static final String MOD_ID = "create_little_networks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateLittleNetworks(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModPackagePortTargetTypes.register(modBus);
        modBus.addListener(ModPayloads::register);
        modBus.addListener(CreateLittleNetworks::addCreativeItems);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.sanhiruzu.createlittlenetworks.client.PanelClient.register(modBus);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, CourierConfig.SERVER_SPEC,
                "create-little-networks-server.toml");
        var id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "little_logistics_depot");
        boolean registered = Schedule.INSTRUCTION_TYPES.stream().map(Pair::getFirst).noneMatch(id::equals);
        if (registered) Schedule.INSTRUCTION_TYPES.add(Pair.of(id, LittleLogisticsDepotInstruction::new));
        DockingStationServices.register(CourierDockService.INSTANCE);
        LOGGER.info("Create: Little Networks initialized");
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) event.accept(ModItems.MIMIC_PANEL.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ModItems.PANEL_DESIGNER.get());
    }
}
