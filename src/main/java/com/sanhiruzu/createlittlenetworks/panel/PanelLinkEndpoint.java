package com.sanhiruzu.createlittlenetworks.panel;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;

final class PanelLinkEndpoint implements IRedstoneLinkable {
    private final MimicPanelBlockEntity owner;
    private final PanelWidget widget;
    private final Couple<Frequency> key;

    PanelLinkEndpoint(MimicPanelBlockEntity owner, PanelWidget widget) {
        this.owner = owner;
        this.widget = widget;
        this.key = Couple.create(Frequency.of(widget.binding().firstFrequency()),
                Frequency.of(widget.binding().secondFrequency()));
    }

    PanelWidget widget() { return widget; }

    @Override
    public int getTransmittedStrength() {
        return widget.type().isControl() ? widget.controlValue() : 0;
    }

    @Override
    public void setReceivedStrength(int power) {
        if (widget.type().isControl() || widget.value() == power) return;
        widget.setValue(power);
        owner.runtimeChanged();
    }

    @Override
    public boolean isListening() {
        return !widget.type().isControl();
    }

    @Override
    public boolean isAlive() {
        return owner.isEndpointAlive();
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return key;
    }

    @Override
    public BlockPos getLocation() {
        return owner.getBlockPos();
    }
}
