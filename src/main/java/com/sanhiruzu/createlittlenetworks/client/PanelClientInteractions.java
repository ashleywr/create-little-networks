package com.sanhiruzu.createlittlenetworks.client;

import com.sanhiruzu.createlittlenetworks.network.PanelEditPayload;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlock;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlockEntity;
import com.sanhiruzu.createlittlenetworks.panel.PanelOrientation;
import com.sanhiruzu.createlittlenetworks.panel.PanelWidgetType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PanelClientInteractions {
    private static PanelWidgetType selectedWidget;
    private static PanelEditPayload.Operation selectedOperation = PanelEditPayload.Operation.PLACE;

    private PanelClientInteractions() {}

    public static void selectWidget(PanelWidgetType widget) {
        selectedWidget = widget;
        selectedOperation = PanelEditPayload.Operation.PLACE;
    }

    public static void selectOperation(PanelEditPayload.Operation operation) {
        selectedOperation = operation;
        if (selectedWidget == null) selectedWidget = PanelWidgetType.TRACE;
    }

    public static String selectionName() {
        if (selectedWidget == null) return "No tool selected";
        return selectedOperation == PanelEditPayload.Operation.PLACE
                ? selectedWidget.serializedName().replace('_', ' ')
                : selectedOperation.name().toLowerCase().replace('_', ' ');
    }

    static PanelWidgetType selectedWidget() { return selectedWidget; }
    static PanelEditPayload.Operation selectedOperation() { return selectedOperation; }

    public static void openRadial() {
        Minecraft.getInstance().setScreen(new PanelRadialScreen());
    }

    public static void useDesigner(UseOnContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (context.getPlayer() == null) return;
        if (context.getPlayer().isShiftKeyDown() || selectedWidget == null) {
            openRadial();
            return;
        }
        BlockEntity found = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(found instanceof MimicPanelBlockEntity tile)) return;
        MimicPanelBlockEntity controller = tile.controller();
        if (controller == null) return;
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof MimicPanelBlock)) return;
        int cellX = tile.tileX() * PanelOrientation.CELLS_PER_BLOCK
                + PanelOrientation.cellX(context.getClickedPos(), context.getClickLocation(), state.getValue(MimicPanelBlock.FACING));
        int cellY = tile.tileY() * PanelOrientation.CELLS_PER_BLOCK
                + PanelOrientation.cellY(context.getClickedPos(), context.getClickLocation(), state.getValue(MimicPanelBlock.FACING));
        PacketDistributor.sendToServer(new PanelEditPayload(context.getClickedPos(), controller.document().revision(),
                selectedOperation, selectedWidget, cellX, cellY, context.getPlayer().isShiftKeyDown()));
        if (minecraft.player != null) minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Panel: " + selectionName()), true);
    }
}
