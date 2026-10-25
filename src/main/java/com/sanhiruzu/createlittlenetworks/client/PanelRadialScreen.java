package com.sanhiruzu.createlittlenetworks.client;

import com.sanhiruzu.createlittlenetworks.network.PanelEditPayload;
import com.sanhiruzu.createlittlenetworks.panel.PanelWidgetType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PanelRadialScreen extends Screen {
    private final PanelRadialMenuRenderer renderer = new PanelRadialMenuRenderer();
    private List<Entry> entries = List.of();
    private Component heading = getTitle();
    private boolean root = true;
    private int keyboardSelected = -1;

    public PanelRadialScreen() {
        super(Component.translatable("screen.create_little_networks.panel_designer"));
    }

    @Override
    protected void init() {
        showCategories();
    }

    private void showCategories() {
        List<Entry> categories = new ArrayList<>();
        for (PanelWidgetType.Category category : PanelWidgetType.Category.values()) {
            Item icon = switch (category) {
                case DRAWING -> Items.STRING;
                case INSTRUMENTS -> Items.COMPASS;
                case CONTROLS -> Items.LEVER;
                case NETWORK -> Items.RAIL;
                case LABELS -> Items.NAME_TAG;
            };
            categories.add(entry(title(category.name()), icon, () -> showCategory(category)));
        }
        categories.add(entry("Edit", Items.SHEARS, this::showEdit));
        show(getTitle(), categories, true);
    }

    private void showCategory(PanelWidgetType.Category category) {
        List<Entry> widgets = new ArrayList<>();
        for (PanelWidgetType type : PanelWidgetType.inCategory(category)) {
            widgets.add(new Entry(Component.literal(title(type.serializedName())), new ItemStack(widgetIcon(type)),
                    () -> choose(type), type, null));
        }
        show(Component.literal(title(category.name())), widgets, false);
    }

    private void showEdit() {
        show(Component.literal("Edit"), List.of(
                edit("Delete", Items.SHEARS, PanelEditPayload.Operation.DELETE),
                edit("Rotate", Items.COMPASS, PanelEditPayload.Operation.ROTATE),
                edit("Recolor", Items.CYAN_DYE, PanelEditPayload.Operation.RECOLOR),
                edit("Duplicate", Items.PAPER, PanelEditPayload.Operation.DUPLICATE),
                edit("Frequency 1", Items.REDSTONE, PanelEditPayload.Operation.BIND_FREQUENCY_ONE),
                edit("Frequency 2", Items.REPEATER, PanelEditPayload.Operation.BIND_FREQUENCY_TWO),
                edit("Bind Network", Items.MAP, PanelEditPayload.Operation.BIND_NETWORK)), false);
    }

    private void show(Component title, List<Entry> entries, boolean root) {
        this.heading = title;
        this.entries = List.copyOf(entries);
        this.root = root;
        keyboardSelected = -1;
        renderer.clearState();
    }

    private static Entry entry(String name, Item icon, Runnable action) {
        return new Entry(Component.literal(name), new ItemStack(icon), action, null, null);
    }

    private Entry edit(String name, Item icon, PanelEditPayload.Operation operation) {
        return new Entry(Component.literal(name), new ItemStack(icon), () -> {
            PanelClientInteractions.selectOperation(operation);
            onClose();
        }, null, operation);
    }

    private void choose(PanelWidgetType type) {
        PanelClientInteractions.selectWidget(type);
        onClose();
    }

    private int selectedIndex() {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.widget != null && PanelClientInteractions.selectedOperation() == PanelEditPayload.Operation.PLACE
                    && entry.widget == PanelClientInteractions.selectedWidget()) return i;
            if (entry.operation != null && entry.operation == PanelClientInteractions.selectedOperation()) return i;
        }
        return -1;
    }

    private int hoveredIndex(double mouseX, double mouseY) {
        return keyboardSelected >= 0 ? keyboardSelected
                : RadialMenuLayout.fit(width, height).segmentAt(mouseX - width / 2D, mouseY - height / 2D, entries.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x660D1213);
        RadialMenuLayout layout = RadialMenuLayout.fit(width, height);
        int hovered = hoveredIndex(mouseX, mouseY);
        renderer.render(graphics, entries, layout, selectedIndex(), hovered);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(heading, (int) (layout.innerRadius() * 1.65F));
        int y = height / 2 - (lines.size() * font.lineHeight) / 2 - 5;
        for (var line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xFFF3E8CF);
            y += font.lineHeight;
        }
        graphics.drawCenteredString(font, root ? "Choose" : "Back", width / 2, y + 3, 0xFF76DDE8);
        String hint = hovered >= 0 ? entries.get(hovered).title.getString() : PanelClientInteractions.selectionName();
        graphics.drawCenteredString(font, hint, width / 2, height - 23, 0xFFF3E8CF);
        graphics.drawCenteredString(font, "Click to select · Right-click / Esc " + (root ? "closes" : "goes back"),
                width / 2, height - 12, 0xFFABC2C4);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboardSelected = -1;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int hovered = RadialMenuLayout.fit(width, height).segmentAt(mouseX - width / 2D, mouseY - height / 2D, entries.size());
            if (hovered >= 0) entries.get(hovered).action.run();
            else if (!root) showCategories();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) cycle(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> back();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> cycle(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> cycle(hasShiftDown() ? -1 : 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (keyboardSelected >= 0) entries.get(keyboardSelected).action.run();
            }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private void cycle(int direction) {
        if (!entries.isEmpty()) keyboardSelected = keyboardSelected < 0 ? (direction > 0 ? 0 : entries.size() - 1)
                : Math.floorMod(keyboardSelected + direction, entries.size());
    }

    private void back() {
        if (root) onClose();
        else showCategories();
    }

    @Override
    public Component getNarrationMessage() {
        return keyboardSelected >= 0 ? heading.copy().append(": ").append(entries.get(keyboardSelected).title) : heading;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static Item widgetIcon(PanelWidgetType type) {
        return switch (type) {
            case TRACE -> Items.STRING;
            case ARROW -> Items.ARROW;
            case ZONE -> Items.CYAN_STAINED_GLASS;
            case LAMP -> Items.REDSTONE_LAMP;
            case GAUGE -> Items.COMPASS;
            case BAR_METER -> Items.COMPARATOR;
            case COUNTER, DEMAND_COUNTER -> Items.REDSTONE;
            case MOMENTARY_BUTTON -> Items.STONE_BUTTON;
            case TOGGLE_SWITCH -> Items.LEVER;
            case SELECTOR -> Items.REPEATER;
            case ROTARY_DIAL -> Items.CLOCK;
            case STATION -> Items.RAIL;
            case BOUND_ROUTE -> Items.POWERED_RAIL;
            case COURIER_STATUS -> Items.MINECART;
            case NETWORK_SUMMARY -> Items.MAP;
            case TEXT_LABEL -> Items.PAPER;
            case NAMEPLATE -> Items.NAME_TAG;
        };
    }

    private static String title(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record Entry(Component title, ItemStack icon, Runnable action, PanelWidgetType widget,
                         PanelEditPayload.Operation operation) implements PanelRadialMenuRenderer.Entry {}
}
