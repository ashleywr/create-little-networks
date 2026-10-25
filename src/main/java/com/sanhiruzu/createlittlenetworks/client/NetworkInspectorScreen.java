package com.sanhiruzu.createlittlenetworks.client;

import com.sanhiruzu.createlittlenetworks.network.NetworkInspectorPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only live snapshot of one Little Logistics transport and courier network. */
public final class NetworkInspectorScreen extends Screen {
    private static final int PANEL = 0xe610141b;
    private static final int PANEL_BORDER = 0xff637083;
    private static final int TEXT = 0xffe8edf2;
    private static final int MUTED = 0xff9aa5b1;
    private static final int HEADING = 0xff82c7ff;
    private static final int GOOD = 0xff78d69a;
    private static final int WARNING = 0xffffcc66;
    private static final int BAD = 0xffff7d7d;
    private static final int LINE_HEIGHT = 11;

    private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
    private NetworkInspectorPayload snapshot;
    private Tab tab = Tab.SUMMARY;
    private int scroll;

    public NetworkInspectorScreen(NetworkInspectorPayload snapshot) {
        super(Component.translatable("screen.create_little_networks.network_inspector"));
        this.snapshot = snapshot;
    }

    public static void open(NetworkInspectorPayload snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof NetworkInspectorScreen inspector
                    && inspector.snapshot.networkId().equals(snapshot.networkId())) {
                inspector.snapshot = snapshot;
                inspector.scroll = 0;
            } else {
                minecraft.setScreen(new NetworkInspectorScreen(snapshot));
            }
        });
    }

    @Override
    protected void init() {
        tabButtons.clear();
        Bounds panel = panel();
        int gap = 4;
        int tabsWidth = panel.width - 20;
        int tabWidth = (tabsWidth - gap * (Tab.values().length - 1)) / Tab.values().length;
        int x = panel.x + 10;
        for (Tab candidate : Tab.values()) {
            Button button = Button.builder(Component.translatable(candidate.translationKey), ignored -> select(candidate))
                    .bounds(x, panel.y + 28, tabWidth, 20).build();
            tabButtons.put(candidate, addRenderableWidget(button));
            x += tabWidth + gap;
        }
        updateTabButtons();
    }

    private void select(Tab selected) {
        tab = selected;
        scroll = 0;
        updateTabButtons();
    }

    private void updateTabButtons() {
        tabButtons.forEach((candidate, button) -> button.active = candidate != tab);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Bounds panel = panel();
        graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL_BORDER);
        graphics.fill(panel.x + 1, panel.y + 1, panel.right() - 1, panel.bottom() - 1, PANEL);
        graphics.drawString(font, title, panel.x + 10, panel.y + 9, TEXT, false);
        graphics.drawString(font, Component.literal(snapshot.name()).withStyle(ChatFormatting.AQUA),
                panel.right() - 10 - font.width(snapshot.name()), panel.y + 9, HEADING, false);

        renderLines(graphics, panel);
        Component footer = Component.translatable("screen.create_little_networks.network_inspector.hint");
        graphics.drawString(font, footer, panel.x + 10, panel.bottom() - 14, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLines(GuiGraphics graphics, Bounds panel) {
        int left = panel.x + 10;
        int top = panel.y + 55;
        int right = panel.right() - 10;
        int bottom = panel.bottom() - 21;
        int width = right - left - 7;

        List<VisualLine> lines = wrap(lines(), width);
        int visible = Math.max(1, (bottom - top) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        graphics.enableScissor(left, top, right, bottom);
        int count = Math.min(visible, lines.size() - scroll);
        for (int i = 0; i < count; i++) {
            VisualLine line = lines.get(scroll + i);
            graphics.drawString(font, line.text, left, top + i * LINE_HEIGHT, line.color, false);
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = right - 2;
            graphics.fill(trackX, top, trackX + 2, bottom, 0xff313945);
            int trackHeight = bottom - top;
            int thumbHeight = Math.max(12, trackHeight * visible / lines.size());
            int thumbY = top + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xff82c7ff);
        }
    }

    private List<VisualLine> wrap(List<InspectorLine> source, int width) {
        List<VisualLine> result = new ArrayList<>();
        for (InspectorLine line : source) {
            if (line.text.getString().isEmpty()) {
                result.add(new VisualLine(net.minecraft.util.FormattedCharSequence.EMPTY, line.color));
                continue;
            }
            font.split(line.text, width).forEach(part -> result.add(new VisualLine(part, line.color)));
        }
        return result;
    }

    private List<InspectorLine> lines() {
        return switch (tab) {
            case SUMMARY -> summaryLines();
            case NODES -> nodeLines();
            case EDGES -> edgeLines();
            case LOGISTICS -> logisticsLines();
        };
    }

    private List<InspectorLine> summaryLines() {
        List<InspectorLine> lines = new ArrayList<>();
        lines.add(heading("Transport graph"));
        lines.add(value("Network ID", snapshot.networkId().toString()));
        lines.add(value("Mode", snapshot.mode()));
        lines.add(value("Revision", Long.toString(snapshot.revision())));
        lines.add(value("Snapshot world tick", Long.toString(snapshot.gameTime())));
        lines.add(blank());
        long stationCount = snapshot.nodes().stream().filter(node -> node.stationId() != null).count();
        lines.add(value("Nodes", snapshot.nodes().size() + " (" + stationCount + " stations)"));
        lines.add(value("Directed edges", Integer.toString(snapshot.edges().size())));
        lines.add(value("Package endpoints", Integer.toString(snapshot.endpoints().size())));
        lines.add(value("Enrolled couriers", Integer.toString(snapshot.couriers().size())));
        lines.add(value("Active jobs", Integer.toString(snapshot.jobs().size())));
        int waiting = snapshot.endpoints().stream().flatMap(endpoint -> endpoint.demand().stream())
                .mapToInt(demand -> Math.max(0, demand.available() - demand.reserved())).sum();
        int reserved = snapshot.endpoints().stream().flatMap(endpoint -> endpoint.demand().stream())
                .mapToInt(NetworkInspectorPayload.DemandView::reserved).sum();
        lines.add(value("Unreserved outbound packages", Integer.toString(waiting)));
        lines.add(value("Reserved packages", Integer.toString(reserved)));
        if (snapshot.nodes().isEmpty()) lines.add(warning("No nodes have been recorded on this chart."));
        if (!snapshot.nodes().isEmpty() && snapshot.edges().isEmpty()) {
            lines.add(warning("The graph has nodes but no directed edges."));
        }
        return lines;
    }

    private List<InspectorLine> nodeLines() {
        List<InspectorLine> lines = new ArrayList<>();
        lines.add(heading("Nodes (" + snapshot.nodes().size() + ")"));
        if (snapshot.nodes().isEmpty()) return List.of(heading("Nodes (0)"), muted("No nodes recorded."));
        for (NetworkInspectorPayload.NodeView node : snapshot.nodes()) {
            lines.add(normal(node.name() + "  [" + shortId(node.id()) + "]"));
            lines.add(muted("  " + node.dimension() + "  " + pos(node.position())
                    + (node.stationId() == null ? "  waypoint" : "  station " + shortId(node.stationId()))));
        }
        return lines;
    }

    private List<InspectorLine> edgeLines() {
        List<InspectorLine> lines = new ArrayList<>();
        lines.add(heading("Directed edges (" + snapshot.edges().size() + ")"));
        if (snapshot.edges().isEmpty()) return List.of(heading("Directed edges (0)"), muted("No edges compiled."));
        Map<UUID, String> names = nodeNames();
        for (NetworkInspectorPayload.EdgeView edge : snapshot.edges()) {
            lines.add(normal(names.getOrDefault(edge.from(), shortId(edge.from())) + "  ->  "
                    + names.getOrDefault(edge.to(), shortId(edge.to()))));
            lines.add(muted("  cost " + formatCost(edge.cost()) + "  " + shortId(edge.from())
                    + " -> " + shortId(edge.to())));
        }
        return lines;
    }

    private List<InspectorLine> logisticsLines() {
        List<InspectorLine> lines = new ArrayList<>();
        Map<UUID, String> stations = stationNames();
        lines.add(heading("Endpoints (" + snapshot.endpoints().size() + ")"));
        if (snapshot.endpoints().isEmpty()) lines.add(muted("No Packagers or Postboxes are known on this network."));
        for (NetworkInspectorPayload.EndpointView endpoint : snapshot.endpoints()) {
            String address = endpoint.address().isBlank() ? "<no local address>" : endpoint.address();
            lines.add(normal(endpoint.kind() + "  " + address + "  [" + shortId(endpoint.id()) + "]"));
            lines.add(muted("  station " + stations.getOrDefault(endpoint.stationId(), shortId(endpoint.stationId()))
                    + "  dock " + pos(endpoint.dockPos()) + "  port " + pos(endpoint.portPos())));
            if (endpoint.demand().isEmpty()) lines.add(muted("  outbound: none"));
            for (NetworkInspectorPayload.DemandView demand : endpoint.demand()) {
                int free = Math.max(0, demand.available() - demand.reserved());
                lines.add(new InspectorLine(Component.literal("  " + demand.address() + ": " + demand.available()
                        + " available, " + demand.reserved() + " reserved, " + free + " free"),
                        free > 0 ? GOOD : MUTED));
            }
        }

        lines.add(blank());
        lines.add(heading("Couriers (" + snapshot.couriers().size() + ")"));
        if (snapshot.couriers().isEmpty()) lines.add(muted("No vehicles are enrolled on this network."));
        for (NetworkInspectorPayload.CourierView courier : snapshot.couriers()) {
            lines.add(normal("Vehicle " + shortId(courier.vehicleId()) + "  "
                    + courier.courierMode() + "  filter " + courier.addressFilter()));
            lines.add(new InspectorLine(Component.literal("  status " + courier.status()),
                    courier.status().startsWith("BLOCKED") || courier.status().equals("MISSING_NETWORK")
                            ? BAD : courier.status().startsWith("WAITING") ? MUTED : GOOD));
            String current = stations.getOrDefault(courier.currentStationId(), shortId(courier.currentStationId()));
            String location = courier.courierMode().equals("DEPOT")
                    ? "depot " + stations.getOrDefault(courier.homeStationId(), shortId(courier.homeStationId()))
                    + "  current " + current
                    : "current " + current;
            lines.add(muted("  " + location + "  pickup " + ticks(courier.pickupDelay())
                    + "  delivery " + ticks(courier.deliveryDelay())));
        }

        lines.add(blank());
        lines.add(heading("Active jobs (" + snapshot.jobs().size() + ")"));
        if (snapshot.jobs().isEmpty()) lines.add(muted("No deliveries are currently active."));
        for (NetworkInspectorPayload.JobView job : snapshot.jobs()) {
            int color = job.state().equals("RECOVERY_HOLD") ? BAD
                    : job.state().equals("LOADING") || job.state().equals("UNLOADING") ? GOOD : WARNING;
            lines.add(new InspectorLine(Component.literal(job.state() + "  " + job.address()
                    + "  [" + shortId(job.id()) + "]"), color));
            lines.add(muted("  vehicle " + shortId(job.vehicleId()) + "  loaded " + job.loaded() + "/"
                    + job.reserved() + "  age " + ticks(snapshot.gameTime() - job.createdAt())
                    + "  idle " + ticks(snapshot.gameTime() - job.lastProgress())));
            lines.add(muted("  endpoint " + shortId(job.sourceEndpointId()) + " -> "
                    + shortId(job.destinationEndpointId())));
        }
        return lines;
    }

    private Map<UUID, String> nodeNames() {
        Map<UUID, String> result = new HashMap<>();
        snapshot.nodes().forEach(node -> result.put(node.id(), node.name()));
        return result;
    }

    private Map<UUID, String> stationNames() {
        Map<UUID, String> result = new HashMap<>();
        snapshot.nodes().forEach(node -> {
            if (node.stationId() != null) result.put(node.stationId(), node.name());
        });
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Bounds panel() {
        int panelWidth = Math.min(560, Math.max(260, width - 20));
        int panelHeight = Math.min(360, Math.max(180, height - 20));
        return new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String pos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String ticks(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0, ticks) / 20.0D);
    }

    private static String formatCost(double cost) {
        return String.format(java.util.Locale.ROOT, "%.1f", cost);
    }

    private static InspectorLine heading(String text) { return new InspectorLine(Component.literal(text), HEADING); }
    private static InspectorLine normal(String text) { return new InspectorLine(Component.literal(text), TEXT); }
    private static InspectorLine muted(String text) { return new InspectorLine(Component.literal(text), MUTED); }
    private static InspectorLine warning(String text) { return new InspectorLine(Component.literal(text), WARNING); }
    private static InspectorLine blank() { return normal(""); }
    private static InspectorLine value(String name, String value) { return normal(name + ": " + value); }

    private enum Tab {
        SUMMARY("screen.create_little_networks.network_inspector.summary"),
        NODES("screen.create_little_networks.network_inspector.nodes"),
        EDGES("screen.create_little_networks.network_inspector.edges"),
        LOGISTICS("screen.create_little_networks.network_inspector.logistics");

        private final String translationKey;
        Tab(String translationKey) { this.translationKey = translationKey; }
    }

    private record InspectorLine(Component text, int color) {}
    private record VisualLine(net.minecraft.util.FormattedCharSequence text, int color) {}
    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }
}
