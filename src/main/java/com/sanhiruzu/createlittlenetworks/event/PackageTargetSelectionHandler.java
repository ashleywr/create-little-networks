package com.sanhiruzu.createlittlenetworks.event;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.routing.LittleLogisticsPackagePortTarget;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import dev.murad.shipping.block.dockingstation.DockingStationBlock;
import dev.murad.shipping.block.dockingstation.DockingStationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** Captures Create's package-target selection through the public NeoForge input event. */
@EventBusSubscriber(modid = CreateLittleNetworks.MOD_ID, value = Dist.CLIENT)
public final class PackageTargetSelectionHandler {
    private PackageTargetSelectionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !AllItemTags.POSTBOXES.matches(minecraft.player.getMainHandItem())
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) return;

        BlockPos clickedPos = hit.getBlockPos();
        if (!(minecraft.level.getBlockState(clickedPos).getBlock() instanceof DockingStationBlock)) return;
        DockingStationBlockEntity dock = DockingStationBlock.getControllerBE(
                minecraft.level.getBlockState(clickedPos), minecraft.level, clickedPos);
        if (dock == null) return;

        BlockPos controllerPos = dock.getBlockPos();
        PackagePortTargetSelectionHandler.exactPositionOfTarget = Vec3.atCenterOf(controllerPos);
        PackagePortTargetSelectionHandler.activePackageTarget =
                new LittleLogisticsPackagePortTarget(controllerPos);
        PackagePortTargetSelectionHandler.isPostbox = true;
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}
