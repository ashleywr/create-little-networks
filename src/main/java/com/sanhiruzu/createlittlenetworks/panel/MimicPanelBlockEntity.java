package com.sanhiruzu.createlittlenetworks.panel;

import com.sanhiruzu.createlittlenetworks.registry.ModBlockEntities;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MimicPanelBlockEntity extends BlockEntity {
    private UUID regionId = UUID.randomUUID();
    private BlockPos controllerPos;
    private int tileX;
    private int tileY;
    private int regionWidth = 1;
    private int regionHeight = 1;
    private PanelDocument document = new PanelDocument();
    private final List<PanelLinkEndpoint> endpoints = new ArrayList<>();
    private final Map<UUID, Integer> momentaryTimers = new HashMap<>();
    private long endpointRevision = Long.MIN_VALUE;
    private int networkPoll;
    private boolean runtimeDirty;

    public MimicPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MIMIC_PANEL.get(), pos, state);
        controllerPos = pos.immutable();
    }

    public UUID regionId() { return regionId; }
    public BlockPos controllerPos() { return controllerPos; }
    public int tileX() { return tileX; }
    public int tileY() { return tileY; }
    public int regionWidth() { return regionWidth; }
    public int regionHeight() { return regionHeight; }
    public boolean isController() { return worldPosition.equals(controllerPos); }
    public PanelDocument document() { return isController() ? document : controller() == null ? document : controller().document; }

    public MimicPanelBlockEntity controller() {
        if (level == null) return null;
        if (isController()) return this;
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        return blockEntity instanceof MimicPanelBlockEntity panel && panel.regionId.equals(regionId) ? panel : null;
    }

    public PanelRegionSnapshot snapshot() {
        MimicPanelBlockEntity controller = controller();
        if (controller == null) return null;
        Direction facing = controller.getBlockState().getValue(MimicPanelBlock.FACING);
        return new PanelRegionSnapshot(controller.regionId, controller.worldPosition.immutable(), facing,
                controller.regionWidth, controller.regionHeight, controller.document);
    }

    void configureRegion(UUID newRegionId, BlockPos newController, int newTileX, int newTileY,
                         int width, int height, PanelDocument newDocument) {
        boolean wasController = isController();
        if (wasController) clearEndpoints();
        regionId = newRegionId;
        controllerPos = newController.immutable();
        tileX = newTileX;
        tileY = newTileY;
        regionWidth = width;
        regionHeight = height;
        document = isController() && newDocument != null ? newDocument : new PanelDocument();
        endpointRevision = Long.MIN_VALUE;
        setChanged();
        syncBlock();
    }

    public void bindNetwork(UUID networkId, String mode) {
        MimicPanelBlockEntity controller = controller();
        if (controller == null || controller.level == null || controller.level.isClientSide) return;
        controller.document.bindNetwork(networkId, mode);
        controller.documentChanged();
    }

    public boolean addWidget(PanelWidget widget, long expectedRevision) {
        if (!isController() || document.revision() != expectedRevision) return false;
        int maxX = regionWidth * PanelOrientation.CELLS_PER_BLOCK;
        int maxY = regionHeight * PanelOrientation.CELLS_PER_BLOCK;
        if (widget.x() < 0 || widget.y() < 0 || widget.maxX() > maxX || widget.maxY() > maxY) return false;
        if (!document.add(widget)) return false;
        documentChanged();
        return true;
    }

    public boolean removeWidget(UUID widgetId, long expectedRevision) {
        if (!isController() || document.revision() != expectedRevision || !document.remove(widgetId)) return false;
        documentChanged();
        return true;
    }

    public PanelWidget widgetAt(int cellX, int cellY) {
        List<PanelWidget> widgets = document().widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            PanelWidget widget = widgets.get(i);
            if (cellX >= widget.x() && cellX < widget.maxX() && cellY >= widget.y() && cellY < widget.maxY()) {
                return widget;
            }
        }
        return null;
    }

    public void mutateWidget(PanelWidget widget, Runnable mutation) {
        if (!isController() || !document.widgets().contains(widget)) return;
        mutation.run();
        document.changed();
        documentChanged();
    }

    public boolean operateAt(int cellX, int cellY, boolean reverse) {
        if (!isController()) return controller() != null && controller().operateAt(cellX, cellY, reverse);
        List<PanelWidget> widgets = document.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            PanelWidget widget = widgets.get(i);
            if (!widget.type().isControl() || cellX < widget.x() || cellX >= widget.maxX()
                    || cellY < widget.y() || cellY >= widget.maxY()) continue;
            switch (widget.type()) {
                case MOMENTARY_BUTTON -> {
                    widget.setControlValue(15);
                    momentaryTimers.put(widget.id(), 10);
                }
                case TOGGLE_SWITCH -> widget.setControlValue(widget.controlValue() == 0 ? 15 : 0);
                case SELECTOR -> widget.setControlValue(step(widget.controlValue(), reverse, 7));
                case ROTARY_DIAL -> widget.setControlValue(Math.floorMod(widget.controlValue() + (reverse ? -1 : 1), 16));
                default -> { return false; }
            }
            transmit(widget.id());
            document.changed();
            documentChanged();
            return true;
        }
        return false;
    }

    private static int step(int current, boolean reverse, int step) {
        if (reverse) return current <= 0 ? 15 : current <= step ? 0 : step;
        return current >= 15 ? 0 : current >= step ? 15 : step;
    }

    public void tick() {
        if (level == null || level.isClientSide || !isController()) return;
        if (endpointRevision != document.revision()) refreshEndpoints();
        tickMomentaryButtons();
        if (++networkPoll >= 5) {
            networkPoll = 0;
            if (PanelNetworkEvaluator.update((ServerLevel) level, document)) runtimeDirty = true;
        }
        if (runtimeDirty) {
            runtimeDirty = false;
            syncBlock();
        }
    }

    private void tickMomentaryButtons() {
        if (momentaryTimers.isEmpty()) return;
        List<UUID> expired = new ArrayList<>();
        momentaryTimers.replaceAll((id, ticks) -> {
            if (ticks <= 1) expired.add(id);
            return ticks - 1;
        });
        for (UUID id : expired) {
            momentaryTimers.remove(id);
            document.widget(id).ifPresent(widget -> {
                widget.setControlValue(0);
                transmit(id);
            });
            runtimeDirty = true;
        }
    }

    private void transmit(UUID widgetId) {
        if (level == null) return;
        endpoints.stream().filter(endpoint -> endpoint.widget().id().equals(widgetId))
                .forEach(endpoint -> Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, endpoint));
    }

    private void refreshEndpoints() {
        clearEndpoints();
        if (level == null || level.isClientSide || !isController()) return;
        for (PanelWidget widget : document.widgets()) {
            if (widget.binding().kind() != PanelBinding.Kind.REDSTONE) continue;
            PanelLinkEndpoint endpoint = new PanelLinkEndpoint(this, widget);
            endpoints.add(endpoint);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, endpoint);
        }
        endpointRevision = document.revision();
    }

    private void clearEndpoints() {
        if (level != null && !level.isClientSide) {
            endpoints.forEach(endpoint -> Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, endpoint));
        }
        endpoints.clear();
    }

    boolean isEndpointAlive() {
        return level != null && !isRemoved() && isController() && level.isLoaded(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }

    void runtimeChanged() {
        runtimeDirty = true;
    }

    void documentChanged() {
        endpointRevision = Long.MIN_VALUE;
        setChanged();
        syncBlock();
    }

    private void syncBlock() {
        if (level == null) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && isController()) refreshEndpoints();
    }

    @Override
    public void setRemoved() {
        clearEndpoints();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("regionId", regionId);
        tag.putLong("controller", controllerPos.asLong());
        tag.putInt("tileX", tileX);
        tag.putInt("tileY", tileY);
        tag.putInt("regionWidth", regionWidth);
        tag.putInt("regionHeight", regionHeight);
        if (isController()) tag.put("document", document.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("regionId")) regionId = tag.getUUID("regionId");
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : worldPosition;
        tileX = Math.max(0, tag.getInt("tileX"));
        tileY = Math.max(0, tag.getInt("tileY"));
        regionWidth = Math.max(1, tag.getInt("regionWidth"));
        regionHeight = Math.max(1, tag.getInt("regionHeight"));
        document = tag.contains("document") ? PanelDocument.load(tag.getCompound("document"), registries) : new PanelDocument();
        endpointRevision = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
