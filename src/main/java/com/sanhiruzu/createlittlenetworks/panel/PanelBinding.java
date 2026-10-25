package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.UUID;

public final class PanelBinding {
    public enum Kind { NONE, REDSTONE, NETWORK_NODE, NETWORK_EDGE, COURIER, NETWORK_METRIC }
    public enum Metric { TOTAL_DEMAND, ACTIVE_JOBS, ENROLLED_COURIERS, BLOCKED_COURIERS, STATION_DEMAND }

    private Kind kind = Kind.NONE;
    private ItemStack firstFrequency;
    private ItemStack secondFrequency;
    private UUID firstId;
    private UUID secondId;
    private Metric metric = Metric.TOTAL_DEMAND;

    public static PanelBinding none() { return new PanelBinding(); }

    public static PanelBinding redstone(ItemStack first, ItemStack second) {
        PanelBinding binding = new PanelBinding();
        binding.kind = Kind.REDSTONE;
        binding.firstFrequency = one(first);
        binding.secondFrequency = one(second);
        return binding;
    }

    public static PanelBinding node(UUID node) { return identified(Kind.NETWORK_NODE, node, null); }
    public static PanelBinding edge(UUID from, UUID to) { return identified(Kind.NETWORK_EDGE, from, to); }
    public static PanelBinding courier(UUID courier) { return identified(Kind.COURIER, courier, null); }

    public static PanelBinding metric(Metric metric, UUID optionalTarget) {
        PanelBinding binding = identified(Kind.NETWORK_METRIC, optionalTarget, null);
        binding.metric = metric;
        return binding;
    }

    private static PanelBinding identified(Kind kind, UUID first, UUID second) {
        PanelBinding binding = new PanelBinding();
        binding.kind = kind;
        binding.firstId = first;
        binding.secondId = second;
        return binding;
    }

    private static ItemStack one(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }

    public Kind kind() { return kind; }
    public ItemStack firstFrequency() { return firstFrequency == null ? ItemStack.EMPTY : firstFrequency; }
    public ItemStack secondFrequency() { return secondFrequency == null ? ItemStack.EMPTY : secondFrequency; }
    public UUID firstId() { return firstId; }
    public UUID secondId() { return secondId; }
    public Metric metric() { return metric; }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name().toLowerCase(Locale.ROOT));
        if (firstFrequency != null && !firstFrequency.isEmpty()) tag.put("firstFrequency", firstFrequency.saveOptional(registries));
        if (secondFrequency != null && !secondFrequency.isEmpty()) tag.put("secondFrequency", secondFrequency.saveOptional(registries));
        if (firstId != null) tag.putUUID("firstId", firstId);
        if (secondId != null) tag.putUUID("secondId", secondId);
        tag.putString("metric", metric.name().toLowerCase(Locale.ROOT));
        return tag;
    }

    public static PanelBinding load(CompoundTag tag, HolderLookup.Provider registries) {
        PanelBinding binding = new PanelBinding();
        try { binding.kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { binding.kind = Kind.NONE; }
        if (tag.contains("firstFrequency")) binding.firstFrequency = ItemStack.parseOptional(registries, tag.getCompound("firstFrequency"));
        if (tag.contains("secondFrequency")) binding.secondFrequency = ItemStack.parseOptional(registries, tag.getCompound("secondFrequency"));
        if (tag.hasUUID("firstId")) binding.firstId = tag.getUUID("firstId");
        if (tag.hasUUID("secondId")) binding.secondId = tag.getUUID("secondId");
        try { binding.metric = Metric.valueOf(tag.getString("metric").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { binding.metric = Metric.TOTAL_DEMAND; }
        return binding;
    }

    public PanelBinding copy() {
        PanelBinding copy = new PanelBinding();
        copy.kind = kind;
        copy.firstFrequency = firstFrequency == null ? null : firstFrequency.copy();
        copy.secondFrequency = secondFrequency == null ? null : secondFrequency.copy();
        copy.firstId = firstId;
        copy.secondId = secondId;
        copy.metric = metric;
        return copy;
    }
}
