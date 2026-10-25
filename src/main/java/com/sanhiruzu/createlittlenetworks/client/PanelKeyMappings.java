package com.sanhiruzu.createlittlenetworks.client;

import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlock;
import com.sanhiruzu.createlittlenetworks.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class PanelKeyMappings {
    public static final KeyMapping OPEN_DESIGNER = new KeyMapping("key.create_little_networks.open_panel_designer",
            GLFW.GLFW_KEY_V, "key.categories.create_little_networks");

    private PanelKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DESIGNER);
    }

    public static void tick(ClientTickEvent.Post event) {
        while (OPEN_DESIGNER.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) continue;
            if (!minecraft.player.getMainHandItem().is(ModItems.PANEL_DESIGNER.get())
                    && !minecraft.player.getOffhandItem().is(ModItems.PANEL_DESIGNER.get())) continue;
            if (minecraft.hitResult instanceof BlockHitResult hit
                    && minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof MimicPanelBlock) {
                PanelClientInteractions.openRadial();
            }
        }
    }
}
