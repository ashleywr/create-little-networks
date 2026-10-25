package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

import java.util.UUID;

public final class PanelWidget {
    private final UUID id;
    private PanelWidgetType type;
    private int x;
    private int y;
    private int width;
    private int height;
    private int rotation;
    private DyeColor color;
    private String label;
    private PanelBinding binding;
    private int value;
    private int controlValue;

    public PanelWidget(UUID id, PanelWidgetType type, int x, int y, int width, int height) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        color = DyeColor.LIME;
        label = "";
        binding = PanelBinding.none();
    }

    public static PanelWidget create(PanelWidgetType type, int x, int y) {
        return new PanelWidget(UUID.randomUUID(), type, x, y, type.defaultWidth(), type.defaultHeight());
    }

    public UUID id() { return id; }
    public PanelWidgetType type() { return type; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public int rotation() { return rotation; }
    public DyeColor color() { return color; }
    public String label() { return label; }
    public PanelBinding binding() { return binding; }
    public int value() { return value; }
    public int controlValue() { return controlValue; }
    public int maxX() { return x + width; }
    public int maxY() { return y + height; }

    public void setValue(int value) { this.value = Math.max(0, value); }
    public void setControlValue(int value) { controlValue = Mth.clamp(value, 0, 15); }
    public void setBinding(PanelBinding binding) { this.binding = binding == null ? PanelBinding.none() : binding; }
    public void setLabel(String label) { this.label = label == null ? "" : label.substring(0, Math.min(64, label.length())); }
    public void setColor(DyeColor color) { this.color = color == null ? DyeColor.LIME : color; }
    public void setRotation(int rotation) { this.rotation = Math.floorMod(rotation, 4); }

    public boolean intersects(int left, int bottom, int right, int top) {
        return x < right && maxX() > left && y < top && maxY() > bottom;
    }

    public boolean fullyInside(int left, int bottom, int right, int top) {
        return x >= left && y >= bottom && maxX() <= right && maxY() <= top;
    }

    public PanelWidget translated(int dx, int dy) {
        PanelWidget copy = copy(id);
        copy.x += dx;
        copy.y += dy;
        return copy;
    }

    public PanelWidget duplicated(int dx, int dy) {
        PanelWidget copy = copy(UUID.randomUUID());
        copy.x += dx;
        copy.y += dy;
        return copy;
    }

    public PanelWidget clippedTo(int left, int bottom, int right, int top) {
        if (!intersects(left, bottom, right, top)) return null;
        if (!type.clippingSafe() && !fullyInside(left, bottom, right, top)) return null;
        boolean clipped = !fullyInside(left, bottom, right, top);
        PanelWidget copy = copy(clipped && type.clippingSafe() ? UUID.randomUUID() : id);
        int clippedLeft = Math.max(x, left);
        int clippedBottom = Math.max(y, bottom);
        int clippedRight = Math.min(maxX(), right);
        int clippedTop = Math.min(maxY(), top);
        copy.x = clippedLeft - left;
        copy.y = clippedBottom - bottom;
        copy.width = Math.max(1, clippedRight - clippedLeft);
        copy.height = Math.max(1, clippedTop - clippedBottom);
        return copy;
    }

    private PanelWidget copy(UUID newId) {
        PanelWidget copy = new PanelWidget(newId, type, x, y, width, height);
        copy.rotation = rotation;
        copy.color = color;
        copy.label = label;
        copy.binding = binding.copy();
        copy.value = value;
        copy.controlValue = controlValue;
        return copy;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.serializedName());
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putInt("rotation", rotation);
        tag.putString("color", color.getName());
        tag.putString("label", label);
        tag.put("binding", binding.save(registries));
        tag.putInt("value", value);
        tag.putInt("controlValue", controlValue);
        return tag;
    }

    public static PanelWidget load(CompoundTag tag, HolderLookup.Provider registries) {
        PanelWidget widget = new PanelWidget(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                PanelWidgetType.byName(tag.getString("type")), tag.getInt("x"), tag.getInt("y"),
                Math.max(1, tag.getInt("width")), Math.max(1, tag.getInt("height")));
        widget.rotation = Math.floorMod(tag.getInt("rotation"), 4);
        DyeColor parsed = DyeColor.byName(tag.getString("color"), DyeColor.LIME);
        widget.color = parsed == null ? DyeColor.LIME : parsed;
        widget.setLabel(tag.getString("label"));
        if (tag.contains("binding")) widget.binding = PanelBinding.load(tag.getCompound("binding"), registries);
        widget.value = Math.max(0, tag.getInt("value"));
        widget.controlValue = Mth.clamp(tag.getInt("controlValue"), 0, 15);
        return widget;
    }
}
