package com.appfactory.tidytv.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class DockOrderTest {
    private static List<String> l(String... s) { return new ArrayList<>(Arrays.asList(s)); }

    @Test public void parsesDockApps() {
        assertEquals(l("a.b", "c.d"), DockOrder.parseConfig("{\"x\":1, \"dock_apps\": [ \"a.b\" , \"c.d\" ], \"y\":[\"z\"]}"));
    }

    @Test public void parseDedupesAndSkipsBlank() {
        assertEquals(l("a", "b"), DockOrder.parseConfig("{\"dock_apps\":[\"a\",\"\",\"b\",\"a\"]}"));
    }

    @Test public void parseHandlesGarbage() {
        assertTrue(DockOrder.parseConfig(null).isEmpty());
        assertTrue(DockOrder.parseConfig("not json").isEmpty());
        assertTrue(DockOrder.parseConfig("{\"dock_apps\": 5}").isEmpty());
        assertTrue(DockOrder.parseConfig("{\"dock_apps\": []}").isEmpty());
    }

    @Test public void parseEscapes() {
        assertEquals(l("a/b", "A"), DockOrder.parseConfig("{\"dock_apps\":[\"a\\/b\",\"\\u0041\"]}"));
    }

    @Test public void revision() {
        assertEquals(7, DockOrder.parseRevision(" 7\n"));
        assertEquals(0, DockOrder.parseRevision("x"));
        assertEquals(0, DockOrder.parseRevision(null));
    }

    @Test public void resolveSkipsMissingAndDuplicates() {
        assertEquals(l("a", "c"), DockOrder.resolve(l("a", "gone", "c", "a"), new HashSet<>(Arrays.asList("a", "c", "d"))));
    }

    @Test public void defaultUsesPreferredOrder() {
        HashSet<String> inst = new HashSet<>(Arrays.asList("org.xbmc.kodi", "com.netflix.ninja", "com.spotify.tv.android", "zzz"));
        assertEquals(l("com.netflix.ninja", "com.spotify.tv.android", "org.xbmc.kodi"), DockOrder.defaultSelection(inst, l("zzz")));
    }

    @Test public void defaultFillsWhenFewPreferred() {
        List<String> others = l("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        HashSet<String> inst = new HashSet<>(others);
        inst.add("com.netflix.ninja");
        List<String> r = DockOrder.defaultSelection(inst, others);
        assertEquals(8, r.size());
        assertEquals("com.netflix.ninja", r.get(0));
        assertEquals("a", r.get(1));
        assertEquals("g", r.get(7));
    }

    @Test public void defaultDoesNotDuplicateWhenFilling() {
        HashSet<String> inst = new HashSet<>(Arrays.asList("com.netflix.ninja", "b"));
        assertEquals(l("com.netflix.ninja", "b"), DockOrder.defaultSelection(inst, l("com.netflix.ninja", "b")));
    }

    @Test public void defaultEmptyWhenNothing() {
        assertTrue(DockOrder.defaultSelection(Collections.<String>emptySet(), l()).isEmpty());
    }

    @Test public void moveRightLeftAndClamp() {
        List<String> x = l("a", "b", "c");
        assertEquals(1, DockOrder.move(x, 0, 1));
        assertEquals(l("b", "a", "c"), x);
        assertEquals(0, DockOrder.move(x, 1, -1));
        assertEquals(l("a", "b", "c"), x);
        assertEquals(0, DockOrder.move(x, 0, -1));
        assertEquals(2, DockOrder.move(x, 2, 1));
        assertEquals(l("a", "b", "c"), x);
        assertEquals(5, DockOrder.move(x, 5, 1));
    }

    @Test public void removeAndAdd() {
        List<String> x = l("a", "b");
        assertTrue(DockOrder.remove(x, "a"));
        assertFalse(DockOrder.remove(x, "zz"));
        assertEquals(l("b"), x);
        assertTrue(DockOrder.add(x, "c"));
        assertFalse(DockOrder.add(x, "c"));
        assertFalse(DockOrder.add(x, ""));
        assertEquals(l("b", "c"), x);
    }

    @Test public void configApplication() {
        assertTrue(DockOrder.shouldApplyConfig(true, false, 0, 0));
        assertFalse(DockOrder.shouldApplyConfig(true, true, 3, 3));
        assertTrue(DockOrder.shouldApplyConfig(true, true, 3, 4));
        assertFalse(DockOrder.shouldApplyConfig(false, false, 0, 1));
    }

    @Test public void joinSplitRoundTrip() {
        assertEquals(l("a", "b"), DockOrder.split(DockOrder.join(l("a", "b", "a"))));
        assertTrue(DockOrder.split("").isEmpty());
    }
}
