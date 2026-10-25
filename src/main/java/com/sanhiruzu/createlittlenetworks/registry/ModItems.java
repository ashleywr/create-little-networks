package com.sanhiruzu.createlittlenetworks.registry;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.panel.PanelDesignerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateLittleNetworks.MOD_ID);

    public static final DeferredItem<BlockItem> MIMIC_PANEL = ITEMS.register("mimic_panel",
            () -> new BlockItem(ModBlocks.MIMIC_PANEL.get(), new Item.Properties()));
    public static final DeferredItem<PanelDesignerItem> PANEL_DESIGNER = ITEMS.register("panel_designer",
            () -> new PanelDesignerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
