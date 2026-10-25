package com.sanhiruzu.createlittlenetworks.panel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum PanelWidgetType {
    TRACE(Category.DRAWING, 1, 1, true, true),
    ARROW(Category.DRAWING, 2, 1, true, true),
    ZONE(Category.DRAWING, 2, 2, true, true),
    LAMP(Category.INSTRUMENTS, 1, 1, false, false),
    GAUGE(Category.INSTRUMENTS, 2, 2, true, false),
    BAR_METER(Category.INSTRUMENTS, 1, 3, true, false),
    COUNTER(Category.INSTRUMENTS, 2, 1, true, false),
    MOMENTARY_BUTTON(Category.CONTROLS, 1, 1, false, false),
    TOGGLE_SWITCH(Category.CONTROLS, 1, 1, false, false),
    SELECTOR(Category.CONTROLS, 2, 2, false, false),
    ROTARY_DIAL(Category.CONTROLS, 2, 2, false, false),
    STATION(Category.NETWORK, 2, 1, true, false),
    BOUND_ROUTE(Category.NETWORK, 2, 1, true, true),
    COURIER_STATUS(Category.NETWORK, 2, 1, true, false),
    DEMAND_COUNTER(Category.NETWORK, 2, 1, true, false),
    NETWORK_SUMMARY(Category.NETWORK, 3, 2, true, false),
    TEXT_LABEL(Category.LABELS, 2, 1, true, false),
    NAMEPLATE(Category.LABELS, 3, 1, true, false);

    public enum Category { DRAWING, INSTRUMENTS, CONTROLS, NETWORK, LABELS }

    private final Category category;
    private final int defaultWidth;
    private final int defaultHeight;
    private final boolean resizable;
    private final boolean clippingSafe;

    PanelWidgetType(Category category, int defaultWidth, int defaultHeight, boolean resizable, boolean clippingSafe) {
        this.category = category;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.resizable = resizable;
        this.clippingSafe = clippingSafe;
    }

    public Category category() { return category; }
    public int defaultWidth() { return defaultWidth; }
    public int defaultHeight() { return defaultHeight; }
    public boolean resizable() { return resizable; }
    public boolean clippingSafe() { return clippingSafe; }
    public String serializedName() { return name().toLowerCase(Locale.ROOT); }

    public static PanelWidgetType byName(String name) {
        return Arrays.stream(values()).filter(value -> value.serializedName().equals(name)).findFirst().orElse(TRACE);
    }

    public static List<PanelWidgetType> inCategory(Category category) {
        return Arrays.stream(values()).filter(type -> type.category == category).toList();
    }

    public boolean isControl() {
        return category == Category.CONTROLS;
    }
}
