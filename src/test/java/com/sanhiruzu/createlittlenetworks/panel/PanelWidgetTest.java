package com.sanhiruzu.createlittlenetworks.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanelWidgetTest {
    @Test
    void tracesClipAcrossChildRegions() {
        PanelWidget trace = new PanelWidget(java.util.UUID.randomUUID(), PanelWidgetType.TRACE, 6, 2, 6, 1);
        PanelWidget left = trace.clippedTo(0, 0, 8, 8);
        PanelWidget right = trace.clippedTo(8, 0, 16, 8);
        assertNotNull(left);
        assertNotNull(right);
        assertEquals(2, left.width());
        assertEquals(4, right.width());
        assertEquals(0, right.x());
        assertNotEquals(left.id(), right.id());
    }

    @Test
    void atomicWidgetsAreDroppedWhenPartiallyCut() {
        PanelWidget gauge = new PanelWidget(java.util.UUID.randomUUID(), PanelWidgetType.GAUGE, 7, 1, 2, 2);
        assertNull(gauge.clippedTo(0, 0, 8, 8));
        assertNotNull(gauge.clippedTo(0, 0, 16, 8));
    }

    @Test
    void labelsAreCappedAtSixtyFourCharacters() {
        PanelWidget label = PanelWidget.create(PanelWidgetType.TEXT_LABEL, 0, 0);
        label.setLabel("x".repeat(100));
        assertEquals(64, label.label().length());
    }
}
