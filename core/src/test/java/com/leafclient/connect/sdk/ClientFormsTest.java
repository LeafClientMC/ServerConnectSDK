package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectOutbound;
import com.leafclient.connect.core.ConnectPanel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientFormsTest {

    final List<Rejection> rejected = new ArrayList<Rejection>();
    final List<Click> clicks = new ArrayList<Click>();

    ClientSim sim() {
        ClientSim c = new ClientSim();
        c.connect.onRejected(rejected::add);
        c.connect.onClick(clicks::add);
        return c;
    }

    @Test
    void textTheClientLetsThePlayerTypeIsAcceptedAndCleaned() {
        String[] typed = {"5§ coins", "a b", "؜abc", "line1\nline2"};
        String[] expected = {"5 coins", "a b", "abc", "line1 line2"};
        for (int i = 0; i < typed.length; i++) {
            rejected.clear();
            clicks.clear();
            ClientSim c = sim();
            Session s = c.hello();
            s.send(Panel.create("form", Place.menu("t")).content(Ui.input("name"), Ui.button("go", "Go")));
            c.flush();
            c.assertClean();
            ConnectPanel p = c.panel("form");
            ConnectElement e = c.element("form", "name");
            c.state.forms().setText(p.id, e, typed[i]);
            c.advance(300);
            c.press("menu", "form", "go");
            assertTrue(rejected.isEmpty(), "typed " + i + " rejected: " + rejected);
            assertEquals(expected[i], clicks.get(0).inputs().text("name"));
        }
    }

    @Test
    void startingTextGoesBackUnchangedAndIsCleanedTheSameWay() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("form", Place.menu("t")).content(Ui.input("name").value("§aSteve"), Ui.button("go", "Go")));
        c.flush();
        c.assertClean();
        c.advance(300);
        c.press("menu", "form", "go");
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals("aSteve", clicks.get(0).inputs().text("name"));
    }

    @Test
    void startingTextTheClientWouldChangeIsRefusedWhenBuilt() {
        assertThrows(InvalidUiException.class, () -> Ui.input("name").value("a\u0001b"));
        assertThrows(InvalidUiException.class, () -> Ui.input("name").value("left‮right"));
    }

    @Test
    void sliderValuesTheClientRoundsAreAccepted() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("form", Place.menu("t")).content(Ui.slider("v", 0, 0.1).step(0.0000015).value(0.0000015), Ui.button("go", "Go")));
        c.flush();
        c.assertClean();
        c.advance(300);
        c.press("menu", "form", "go");
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(1, clicks.size());
    }

    @Test
    void blockPanelInAnotherDimensionIsClickableThere() {
        ClientSim c = new ClientSim();
        String[] dim = {"minecraft:overworld"};
        Object[] world = {"overworld-uid"};
        LeafConnect w = new LeafConnect(ConnectLog.silent(), () -> c.clock[0]) {
            @Override
            protected Object worldOf(Session session) {
                return world[0];
            }

            @Override
            protected String dimensionOf(Session session) {
                return dim[0];
            }
        };
        w.onRejected(rejected::add);
        w.onClick(clicks::add);
        JsonObject hello = ConnectOutbound.hello("2.0.0", "1.21.11", "fabric", ClientSim.CAPS, Arrays.asList("x"), ConnectOutbound.defaultPrefs());
        w.receive(c.id, "Steve", c.player, ConnectOutbound.encode(hello), c.wire);
        Session s = w.session(c.id);
        s.send(Panel.create("portal", Place.block(10, 64, 10).dim("minecraft:the_nether")).content(Ui.button("b", "Enter")));
        w.flush();
        c.deliverAll();
        c.assertClean();
        world[0] = "nether-uid";
        dim[0] = "minecraft:the_nether";
        c.advance(300);
        w.receive(c.id, "Steve", c.player, ConnectOutbound.encode(c.clickJson("block", "portal", "b", null, false)), c.wire);
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(1, clicks.size());
        dim[0] = "minecraft:the_end";
        c.advance(300);
        w.receive(c.id, "Steve", c.player, ConnectOutbound.encode(c.clickJson("block", "portal", "b", null, false)), c.wire);
        assertEquals(Rejection.Reason.OUT_OF_REACH, rejected.get(0).reason());
    }
}
