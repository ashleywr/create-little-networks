package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PanelDocument {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_WIDGETS = 1024;

    private final List<PanelWidget> widgets = new ArrayList<>();
    private long revision;
    private UUID networkId;
    private String networkMode = "";

    public List<PanelWidget> widgets() { return widgets; }
    public long revision() { return revision; }
    public UUID networkId() { return networkId; }
    public String networkMode() { return networkMode; }
    public boolean isNetworkBound() { return networkId != null; }
    public void changed() { revision++; }

    public boolean add(PanelWidget widget) {
        if (widgets.size() >= MAX_WIDGETS) return false;
        widgets.add(widget);
        changed();
        return true;
    }

    public Optional<PanelWidget> widget(UUID id) {
        return widgets.stream().filter(widget -> widget.id().equals(id)).findFirst();
    }

    public boolean remove(UUID id) {
        boolean removed = widgets.removeIf(widget -> widget.id().equals(id));
        if (removed) changed();
        return removed;
    }

    public void bindNetwork(UUID networkId, String mode) {
        this.networkId = networkId;
        this.networkMode = mode == null ? "" : mode;
        changed();
    }

    public void clearNetwork() {
        networkId = null;
        networkMode = "";
        changed();
    }

    public PanelDocument clipped(int left, int bottom, int right, int top) {
        PanelDocument document = new PanelDocument();
        document.revision = revision + 1;
        document.networkId = networkId;
        document.networkMode = networkMode;
        for (PanelWidget widget : widgets) {
            PanelWidget clipped = widget.clippedTo(left, bottom, right, top);
            if (clipped != null) document.widgets.add(clipped);
        }
        return document;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT_VERSION);
        tag.putLong("revision", revision);
        if (networkId != null) tag.putUUID("network", networkId);
        tag.putString("networkMode", networkMode);
        ListTag list = new ListTag();
        widgets.stream().limit(MAX_WIDGETS).forEach(widget -> list.add(widget.save(registries)));
        tag.put("widgets", list);
        return tag;
    }

    public static PanelDocument load(CompoundTag tag, HolderLookup.Provider registries) {
        PanelDocument document = new PanelDocument();
        document.revision = Math.max(0, tag.getLong("revision"));
        if (tag.hasUUID("network")) document.networkId = tag.getUUID("network");
        document.networkMode = tag.getString("networkMode");
        ListTag list = tag.getList("widgets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_WIDGETS); i++) {
            document.widgets.add(PanelWidget.load(list.getCompound(i), registries));
        }
        return document;
    }
}
