package com.sanhiruzu.createlittlenetworks.schedule;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;
import dev.murad.shipping.setup.ModBlocks;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Schedule marker interpreted by the Little Logistics dispatcher rather than Create's train runtime. */
public final class LittleLogisticsDepotInstruction extends TextScheduleInstruction {
    public LittleLogisticsDepotInstruction() {
        data.putString("Text", "*");
    }

    public String getFilter() {
        return getLabelText();
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(getSecondLineIcon(), Component.translatable(
                "create_little_networks.schedule.instruction.little_logistics_depot"));
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return List.of(Component.translatable(
                        "create_little_networks.schedule.instruction.little_logistics_depot.summary"),
                Component.literal("\"" + getFilter() + "\""));
    }

    @Override
    public ItemStack getSecondLineIcon() {
        return ModBlocks.DOCKING_STATION.get().asItem().getDefaultInstance();
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(CreateLittleNetworks.MOD_ID, "little_logistics_depot");
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    @Override
    public @Nullable DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        return null;
    }
}
