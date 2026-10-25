package com.sanhiruzu.createlittlenetworks.event;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.dev.LogisticsVerificationCommand;
import com.sanhiruzu.createlittlenetworks.network.NetworkInspectorPayload;
import dev.murad.shipping.item.NetworkChartItem;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.commands.Commands;

/** Opens a server-authored diagnostics snapshot when a populated Network Chart is used in air. */
@EventBusSubscriber(modid = CreateLittleNetworks.MOD_ID)
public final class NetworkInspectorHandler {
    private NetworkInspectorHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseChart(PlayerInteractEvent.RightClickItem event) {
        var reference = NetworkChartItem.getReference(event.getItemStack());
        if (reference.isEmpty()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;

        open(player, event.getItemStack());
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cln")
                .then(Commands.literal("inspect").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
                    ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
                    if (!open(player, main) && !open(player, offhand)) {
                        PacketDistributor.sendToPlayer(player, NetworkInspectorPayload.empty(player));
                    }
                    return 1;
                })));
        if (!FMLEnvironment.production) LogisticsVerificationCommand.register(event.getDispatcher());
    }

    private static boolean open(ServerPlayer player, ItemStack stack) {
        var reference = NetworkChartItem.getReference(stack);
        if (reference.isEmpty()) return false;
        TransportNetwork network = TransportNetworkData.get(player.server)
                .get(reference.get().networkId()).orElse(null);
        if (network == null) return false;
        PacketDistributor.sendToPlayer(player, NetworkInspectorPayload.create(player, network));
        return true;
    }
}
