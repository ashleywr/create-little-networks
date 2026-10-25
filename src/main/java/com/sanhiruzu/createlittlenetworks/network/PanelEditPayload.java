package com.sanhiruzu.createlittlenetworks.network;

import com.sanhiruzu.createlittlenetworks.CreateLittleNetworks;
import com.sanhiruzu.createlittlenetworks.panel.MimicPanelBlockEntity;
import com.sanhiruzu.createlittlenetworks.panel.PanelBinding;
import com.sanhiruzu.createlittlenetworks.panel.PanelWidget;
import com.sanhiruzu.createlittlenetworks.panel.PanelWidgetType;
import com.sanhiruzu.createlittlenetworks.registry.ModItems;
import dev.murad.shipping.transport.TransportNetwork;
import dev.murad.shipping.transport.TransportNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;
import java.util.List;

public record PanelEditPayload(BlockPos panelPos, long expectedRevision, Operation operation,
                               PanelWidgetType widgetType, int cellX, int cellY, boolean reverse)
        implements CustomPacketPayload {
    public enum Operation {
        PLACE, DELETE, ROTATE, RECOLOR, DUPLICATE, BIND_FREQUENCY_ONE, BIND_FREQUENCY_TWO, BIND_NETWORK
    }

    public static final Type<PanelEditPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateLittleNetworks.MOD_ID, "panel_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelEditPayload> STREAM_CODEC =
            StreamCodec.of(PanelEditPayload::encode, PanelEditPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, PanelEditPayload payload) {
        buf.writeBlockPos(payload.panelPos);
        buf.writeVarLong(payload.expectedRevision);
        buf.writeEnum(payload.operation);
        buf.writeEnum(payload.widgetType);
        buf.writeVarInt(payload.cellX);
        buf.writeVarInt(payload.cellY);
        buf.writeBoolean(payload.reverse);
    }

    private static PanelEditPayload decode(RegistryFriendlyByteBuf buf) {
        return new PanelEditPayload(buf.readBlockPos(), buf.readVarLong(), buf.readEnum(Operation.class),
                buf.readEnum(PanelWidgetType.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PanelEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.getMainHandItem().is(ModItems.PANEL_DESIGNER.get())
                    && !player.getOffhandItem().is(ModItems.PANEL_DESIGNER.get())) return;
            if (player.distanceToSqr(payload.panelPos.getCenter()) > 100) return;
            BlockEntity found = player.level().getBlockEntity(payload.panelPos);
            if (!(found instanceof MimicPanelBlockEntity tile)) return;
            MimicPanelBlockEntity controller = tile.controller();
            if (controller == null || controller.document().revision() != payload.expectedRevision) return;

            if (payload.operation == Operation.PLACE) {
                controller.addWidget(PanelWidget.create(payload.widgetType, payload.cellX, payload.cellY),
                        payload.expectedRevision);
                return;
            }
            PanelWidget widget = controller.widgetAt(payload.cellX, payload.cellY);
            if (widget == null) return;
            switch (payload.operation) {
                case DELETE -> controller.removeWidget(widget.id(), payload.expectedRevision);
                case ROTATE -> controller.mutateWidget(widget, () -> widget.setRotation(widget.rotation() + (payload.reverse ? -1 : 1)));
                case RECOLOR -> controller.mutateWidget(widget, () -> {
                    DyeColor[] colors = DyeColor.values();
                    widget.setColor(colors[Math.floorMod(widget.color().ordinal() + (payload.reverse ? -1 : 1), colors.length)]);
                });
                case DUPLICATE -> controller.addWidget(widget.duplicated(1, 1), payload.expectedRevision);
                case BIND_FREQUENCY_ONE, BIND_FREQUENCY_TWO -> {
                    ItemStack sampled = player.getOffhandItem();
                    if (sampled.is(ModItems.PANEL_DESIGNER.get())) sampled = player.getMainHandItem();
                    ItemStack first = payload.operation == Operation.BIND_FREQUENCY_ONE ? sampled : widget.binding().firstFrequency();
                    ItemStack second = payload.operation == Operation.BIND_FREQUENCY_TWO ? sampled : widget.binding().secondFrequency();
                    ItemStack finalFirst = first;
                    ItemStack finalSecond = second;
                    controller.mutateWidget(widget, () -> widget.setBinding(PanelBinding.redstone(finalFirst, finalSecond)));
                }
                case BIND_NETWORK -> bindNetwork(controller, widget, payload.reverse, player);
                default -> { }
            }
        });
    }

    private static void bindNetwork(MimicPanelBlockEntity controller, PanelWidget widget, boolean reverse,
                                    ServerPlayer player) {
        if (!controller.document().isNetworkBound()) return;
        TransportNetwork network = TransportNetworkData.get(player.server)
                .get(controller.document().networkId()).orElse(null);
        if (network == null) return;
        switch (widget.type()) {
            case STATION -> {
                List<java.util.UUID> values = network.nodes().stream().sorted(Comparator.comparing(node -> node.name()))
                        .map(node -> node.id()).toList();
                java.util.UUID next = cycle(values, widget.binding().firstId(), reverse);
                if (next != null) controller.mutateWidget(widget, () -> widget.setBinding(PanelBinding.node(next)));
            }
            case BOUND_ROUTE -> {
                List<dev.murad.shipping.transport.TransportEdge> values = network.edges().stream()
                        .sorted(Comparator.comparing(edge -> edge.from().toString() + edge.to())).toList();
                int current = -1;
                for (int i = 0; i < values.size(); i++) {
                    var edge = values.get(i);
                    if (edge.from().equals(widget.binding().firstId()) && edge.to().equals(widget.binding().secondId())) current = i;
                }
                if (!values.isEmpty()) {
                    int index = Math.floorMod(current + (reverse ? -1 : 1), values.size());
                    var edge = values.get(index);
                    controller.mutateWidget(widget, () -> widget.setBinding(PanelBinding.edge(edge.from(), edge.to())));
                }
            }
            case COURIER_STATUS -> {
                List<java.util.UUID> values = com.sanhiruzu.createlittlenetworks.routing.CourierNetworkData.get(player.server)
                        .couriers().stream().filter(courier -> network.id().equals(courier.networkId))
                        .map(courier -> courier.vehicleId).sorted().toList();
                java.util.UUID next = cycle(values, widget.binding().firstId(), reverse);
                if (next != null) controller.mutateWidget(widget, () -> widget.setBinding(PanelBinding.courier(next)));
            }
            default -> {
                PanelBinding.Metric[] metrics = PanelBinding.Metric.values();
                int current = widget.binding().kind() == PanelBinding.Kind.NETWORK_METRIC
                        ? widget.binding().metric().ordinal() : -1;
                PanelBinding.Metric next = metrics[Math.floorMod(current + (reverse ? -1 : 1), metrics.length)];
                controller.mutateWidget(widget, () -> widget.setBinding(PanelBinding.metric(next, null)));
            }
        }
    }

    private static <T> T cycle(List<T> values, T current, boolean reverse) {
        if (values.isEmpty()) return null;
        int index = values.indexOf(current);
        return values.get(Math.floorMod(index + (reverse ? -1 : 1), values.size()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
