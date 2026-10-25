package com.sanhiruzu.createlittlenetworks.registry;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateLittleNetworks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MimicPanelBlockEntity>> MIMIC_PANEL =
            BLOCK_ENTITIES.register("mimic_panel", () -> BlockEntityType.Builder.of(
                    MimicPanelBlockEntity::new, ModBlocks.MIMIC_PANEL.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
