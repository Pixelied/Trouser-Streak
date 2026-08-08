package dev.hypershot.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class UiLayoutTest {
    @Test
    void wideScreensUseSidebarAndKeepContentInsideOuterBounds() {
        UiLayout layout = UiLayout.compute(1280, 720, true);
        assertFalse(layout.compact());
        assertTrue(layout.sidebar().width() > 0);
        assertEquals(layout.outer().right(), layout.content().right());
        assertTrue(layout.content().top() >= layout.header().bottom());
        assertTrue(layout.content().bottom() <= layout.footer().top());
    }

    @Test
    void compactScreensCollapseSidebarAndRemainUsable() {
        UiLayout layout = UiLayout.compute(420, 240, true);
        assertTrue(layout.compact());
        assertEquals(0, layout.sidebar().width());
        assertTrue(layout.content().width() >= 320);
        assertTrue(layout.content().height() > 0);
    }

    @Test
    void distributedControlsNeverOverlapAndFillTheRowExactly() {
        UiLayout.Rect row = new UiLayout.Rect(12, 20, 396, 20);
        List<UiLayout.Rect> controls = UiLayout.distribute(row, 5, 6);
        assertEquals(5, controls.size());
        assertEquals(row.left(), controls.getFirst().left());
        assertEquals(row.right(), controls.getLast().right());
        for (int i = 1; i < controls.size(); i++) {
            assertTrue(controls.get(i - 1).right() <= controls.get(i).left());
        }
    }

    @Test
    void invalidDimensionsAndCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UiLayout.compute(0, 240, false));
        assertThrows(IllegalArgumentException.class, () -> UiLayout.distribute(new UiLayout.Rect(0, 0, 10, 10), 0, 2));
    }
}
