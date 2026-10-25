package com.sanhiruzu.createlittlenetworks.panel;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PanelDocumentTest {
    @Test
    void documentRoundTripsVersionedWidgetAndBindings() {
        PanelDocument source = new PanelDocument();
        UUID network = UUID.randomUUID();
        source.bindNetwork(network, "rail");
        PanelWidget widget = PanelWidget.create(PanelWidgetType.GAUGE, 3, 4);
        widget.setColor(DyeColor.ORANGE);
        widget.setLabel("Boiler speed");
        UUID node = UUID.randomUUID();
        widget.setBinding(PanelBinding.node(node));
        source.add(widget);

        PanelDocument loaded = PanelDocument.load(source.save(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(network, loaded.networkId());
        assertEquals("rail", loaded.networkMode());
        assertEquals(1, loaded.widgets().size());
        PanelWidget restored = loaded.widgets().getFirst();
        assertEquals(PanelWidgetType.GAUGE, restored.type());
        assertEquals(DyeColor.ORANGE, restored.color());
        assertEquals("Boiler speed", restored.label());
        assertEquals(PanelBinding.Kind.NETWORK_NODE, restored.binding().kind());
        assertEquals(node, restored.binding().firstId());
    }

    @Test
    void clippedDocumentsTranslateTheirLocalOrigin() {
        PanelDocument source = new PanelDocument();
        source.add(new PanelWidget(UUID.randomUUID(), PanelWidgetType.ZONE, 7, 0, 3, 2));
        PanelDocument child = source.clipped(8, 0, 16, 8);
        assertEquals(1, child.widgets().size());
        assertEquals(0, child.widgets().getFirst().x());
        assertEquals(2, child.widgets().getFirst().width());
    }
}
