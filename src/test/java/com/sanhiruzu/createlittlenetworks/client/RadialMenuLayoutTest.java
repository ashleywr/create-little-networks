package com.sanhiruzu.createlittlenetworks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadialMenuLayoutTest {
    private final RadialMenuLayout layout = new RadialMenuLayout(40, 100);

    @Test
    void centerCannotSelectAWedge() {
        assertEquals(-1, layout.segmentAt(0, 0, 6));
        assertEquals(-1, layout.segmentAt(39, 0, 6));
        assertEquals(-1, layout.segmentAt(70, 0, 0));
    }

    @Test
    void everyWedgeCenterAndBothSidesSelectTheVisibleWedge() {
        for (int count = 3; count <= 8; count++) {
            for (int wedge = 0; wedge < count; wedge++) {
                for (double offset : new double[]{-179D / count, 0, 179D / count}) {
                    double angle = Math.toRadians(-90 + 360D * wedge / count + offset);
                    assertEquals(wedge, layout.segmentAt(Math.cos(angle) * 70, Math.sin(angle) * 70, count),
                            "count=" + count + ", wedge=" + wedge + ", offset=" + offset);
                }
            }
        }
    }

    @Test
    void selectionTracksDirectionBeyondTheRing() {
        assertEquals(0, layout.segmentAt(0, -500, 4));
        assertEquals(1, layout.segmentAt(500, 0, 4));
        assertEquals(2, layout.segmentAt(0, 500, 4));
        assertEquals(3, layout.segmentAt(-500, 0, 4));
    }

    @Test
    void menuFitsSmallGuiWithoutChangingSelectionDirections() {
        RadialMenuLayout small = RadialMenuLayout.fit(285, 160);
        assertTrue(small.outerRadius() <= 60);
        assertEquals(0, small.segmentAt(0, -small.labelRadius(), 6));
        assertEquals(-1, small.segmentAt(0, small.innerRadius() / 2, 6));
    }
}
