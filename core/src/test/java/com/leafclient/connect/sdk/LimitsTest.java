package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitsTest {

    static void sdkRejects(String limit, Executable build) {
        InvalidUiException e = assertThrows(InvalidUiException.class, build);
        assertTrue(e.getMessage().contains(limit), "expected the message to name " + limit + ": " + e.getMessage());
    }

    static void clientAccepts(String messageJson) {
        ClientSim sim = new ClientSim();
        sim.deliver(messageJson.getBytes(StandardCharsets.UTF_8));
        sim.assertClean();
    }

    static String clientRejects(JsonObject message) {
        ClientSim sim = new ClientSim();
        sim.deliver(message.toString().getBytes(StandardCharsets.UTF_8));
        assertFalse(sim.errors.isEmpty(), "the client accepted " + message);
        return sim.errors.get(0);
    }

    static JsonObject json(Message m) {
        return BatchingTest.parse(m.toJson());
    }

    static JsonArray rootChildren(JsonObject panelSet) {
        return panelSet.getAsJsonObject("panel").getAsJsonObject("root").getAsJsonArray("children");
    }

    static JsonObject textElement() {
        JsonObject t = new JsonObject();
        t.addProperty("type", "text");
        t.addProperty("text", "x");
        return t;
    }

    static Panel filled(int texts) {
        Container root = Ui.col();
        for (int i = 0; i < texts; i++) root.add(Ui.text("x"));
        return Panel.create("p", Place.menu()).root(root);
    }

    @Test
    void elementsPerPanelMatchesTheClient() {
        Panel max = filled(Limits.ELEMENTS_PER_PANEL - 1);
        clientAccepts(max.toJson());
        sdkRejects("ELEMENTS_PER_PANEL", () -> filled(Limits.ELEMENTS_PER_PANEL).toJson());
        JsonObject over = json(max);
        rootChildren(over).add(textElement());
        assertTrue(clientRejects(over).contains("more than 128 elements"));
    }

    @Test
    void leaderboardExpansionIsCountedExactlyLikeTheClient() {
        Leaderboard board = Ui.leaderboard();
        for (int i = 0; i < Limits.LEADERBOARD_ROWS; i++) board.row(new UUID(0, i + 1), "Player " + i, Integer.toString(1000 - i));
        Container root = Ui.col(board);
        for (int i = 0; i < 24; i++) root.add(Ui.text("x"));
        Panel exact = Panel.create("p", Place.menu()).root(root);
        clientAccepts(exact.toJson());
        root.add(Ui.text("one too many"));
        sdkRejects("ELEMENTS_PER_PANEL", exact::toJson);
        JsonObject over = json(Panel.create("p", Place.menu()).root(Ui.col(board)));
        for (int i = 0; i < 25; i++) rootChildren(over).add(textElement());
        assertTrue(clientRejects(over).contains("more than 128 elements"));
    }

    @Test
    void leaderboardDepthIsCountedLikeTheClient() {
        Leaderboard board = Ui.leaderboard().row(new UUID(0, 1), "A", "1");
        Element<?> nested = board;
        for (int i = 0; i < 4; i++) nested = Ui.col(nested);
        Panel ok = Panel.create("p", Place.menu()).root(nested);
        clientAccepts(ok.toJson());
        Panel deep = Panel.create("p", Place.menu()).root(Ui.col(nested));
        sdkRejects("ELEMENT_DEPTH", deep::toJson);
        JsonObject over = json(ok);
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "col");
        JsonArray children = new JsonArray();
        children.add(over.getAsJsonObject("panel").get("root"));
        wrapper.add("children", children);
        over.getAsJsonObject("panel").add("root", wrapper);
        assertTrue(clientRejects(over).contains("deeper than 8"));
    }

    @Test
    void buttonsPerPanelMatchesTheClient() {
        Container root = Ui.col();
        for (int i = 0; i < Limits.BUTTONS_PER_PANEL; i++) root.add(Ui.button("b" + i, "B"));
        Panel max = Panel.create("p", Place.chat()).root(root);
        clientAccepts(max.toJson());
        root.add(Ui.button("b48", "B"));
        sdkRejects("BUTTONS_PER_PANEL", max::toJson);
        JsonObject over = json(Panel.create("p", Place.chat()).root(Ui.col()));
        for (int i = 0; i < 49; i++) {
            JsonObject b = new JsonObject();
            b.addProperty("type", "button");
            b.addProperty("id", "b" + i);
            if (rootChildren(over) == null) over.getAsJsonObject("panel").getAsJsonObject("root").add("children", new JsonArray());
            rootChildren(over).add(b);
        }
        assertTrue(clientRejects(over).contains("more than 48 buttons"));
    }

    @Test
    void inputsOnlyInMenuPanelsLikeTheClient() {
        sdkRejects("only allowed in menu panels", () -> Panel.create("p", Place.chat()).content(Ui.input("name")).toJson());
        JsonObject raw = json(Panel.create("p", Place.menu()).content(Ui.input("name")));
        raw.getAsJsonObject("panel").getAsJsonObject("place").addProperty("in", "chat");
        assertTrue(clientRejects(raw).contains("only allowed in menu panels"));
    }

    @Test
    void sessionLimitsMatchTheClient() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        for (int i = 0; i < Limits.PANELS_PER_TAB; i++) s.send(Panel.create("t" + i, Place.menu("shop")).content(Ui.text("x")));
        sdkRejects("PANELS_PER_TAB", () -> s.send(Panel.create("t4", Place.menu("shop")).content(Ui.text("x"))));
        s.send(Panel.create("t0", Place.menu("shop")).content(Ui.text("replaced")));
        for (int i = 0; i < Limits.CHAT_PANELS; i++) s.send(Panel.create("c" + i, Place.chat()).content(Ui.text("x")));
        sdkRejects("CHAT_PANELS", () -> s.send(Panel.create("c4", Place.chat()).content(Ui.text("x"))));
        for (int i = 0; i < Limits.HUD_PANELS; i++) s.send(Panel.create("h" + i, Place.hud(Place.Anchor.TOP)).content(Ui.text("x")));
        sdkRejects("HUD_PANELS", () -> s.send(Panel.create("h6", Place.hud(Place.Anchor.TOP)).content(Ui.text("x"))));
        for (int i = 0; i < Limits.INVENTORY_PANELS_PER_SIDE; i++) s.send(Panel.create("i" + i, Place.inventory(Place.Side.LEFT)).content(Ui.text("x")));
        s.send(Panel.create("ir", Place.inventory(Place.Side.RIGHT)).content(Ui.text("x")));
        sdkRejects("INVENTORY_PANELS_PER_SIDE", () -> s.send(Panel.create("i4", Place.inventory(Place.Side.LEFT)).content(Ui.text("x"))));
        sim.flush();
        sim.assertClean();
        JsonObject fifth = json(Panel.create("t4", Place.menu("shop")).content(Ui.text("x")));
        sim.deliver(fifth.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(sim.errors.get(0).contains("more than 4 panels in one menu tab"), sim.errors.toString());

        ClientSim values = new ClientSim();
        Session v = values.hello();
        Values many = Values.create();
        for (int i = 0; i < Limits.VALUES; i++) many.set("k" + i, i);
        v.send(many);
        sdkRejects("VALUES", () -> v.setValue("one.more", 1));
        values.flush();
        values.assertClean();
        values.deliver("{\"v\":1,\"t\":\"values.set\",\"set\":{\"one.more\":1}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(values.errors.get(0).contains("more than 256 values"));

        ClientSim wps = new ClientSim();
        Session w = wps.hello();
        for (int i = 0; i < Limits.WAYPOINTS; i++) w.send(Waypoint.create("w" + i, "W", i, 64, 0));
        sdkRejects("WAYPOINTS", () -> w.send(Waypoint.create("w64", "W", 0, 64, 0)));
        wps.flush();
        wps.assertClean();
        wps.deliver(json(Waypoint.create("w64", "W", 0, 64, 0)).toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(wps.errors.get(0).contains("more than 64 server waypoints"));
    }

    @Test
    void fieldRulesMatchTheClient() {
        sdkRejects("not a valid id", () -> Panel.create("Bad Id", Place.chat()));
        JsonObject badId = json(Panel.create("ok", Place.chat()).content(Ui.text("x")));
        badId.getAsJsonObject("panel").addProperty("id", "Bad Id");
        assertTrue(clientRejects(badId).contains("not a valid id"));

        sdkRejects("is not a color", () -> Ui.text("x").color("red"));
        JsonObject badColor = json(Panel.create("ok", Place.chat()).content(Ui.text("x").color("#fff")));
        rootChildren(badColor).get(0).getAsJsonObject().addProperty("color", "red");
        assertTrue(clientRejects(badColor).contains("expected #RGB"));

        sdkRejects("more than 16 options", () -> {
            Choice c = Ui.choice("c");
            for (int i = 0; i < 17; i++) c.option("o" + i);
        });
        JsonObject options = json(Panel.create("ok", Place.menu()).content(Ui.choice("c", "a")));
        JsonArray opts = rootChildren(options).get(0).getAsJsonObject().getAsJsonArray("options");
        for (int i = 0; i < 16; i++) opts.add(new JsonPrimitive("o" + i));
        assertTrue(clientRejects(options).contains("more than 16 entries"));

        sdkRejects("too small for this range", () -> Ui.slider("s", 0, 1000000).step(1));
        sdkRejects("not a texture", () -> Ui.image("myserver:Gui/Banner.PNG"));
        JsonObject texture = json(Panel.create("ok", Place.chat()).content(Ui.image("myserver:gui/banner.png")));
        rootChildren(texture).get(0).getAsJsonObject().getAsJsonObject("src").addProperty("texture", "myserver:Gui/Banner.PNG");
        assertTrue(clientRejects(texture).contains("expected namespace:path/to/image.png"));
        sdkRejects("only https links", () -> Ui.webImage("http://example.com/a.png"));
        sdkRejects("a toast needs a title or a body", () -> Toast.create("t").toJson());
        JsonObject toast = json(Toast.create("t").title("x"));
        toast.remove("title");
        assertTrue(clientRejects(toast).contains("a toast needs a title or a body"));

        sdkRejects("at most one of command, url and copy", () -> Panel.create("p", Place.chat()).content(
                Ui.button("b", "B").on(Action.command("/a"), Action.url("https://a.com"))).toJson());
        sdkRejects("MENU_TABS", () -> {
            Menu m = Menu.create("M");
            for (int i = 0; i < 9; i++) m.add(Menu.tab("t" + i, "T"));
        });
        JsonObject menu = json(Menu.create("M").add(Menu.tab("t0", "T")));
        for (int i = 1; i < 9; i++) {
            JsonObject t = new JsonObject();
            t.addProperty("id", "t" + i);
            menu.getAsJsonArray("tabs").add(t);
        }
        assertTrue(clientRejects(menu).contains("more than 8 entries"));

        sdkRejects("condition nested 5 deep", () -> Panel.create("p", Place.chat()).when(
                Condition.not(Condition.not(Condition.not(Condition.not(Condition.sneaking()))))).content(Ui.text("x")).toJson());
        sdkRejects("a scroll cannot contain another scroll", () -> Panel.create("p", Place.menu())
                .root(Ui.scroll(50, Ui.scroll(40, Ui.text("x")))).toJson());
        sdkRejects("click handler", () -> Panel.create("p", Place.chat()).content(Ui.button("b", "B").on(Action.copy("x")).onClick(c -> { })).toJson());
    }

    @Test
    void textLongerThanTheClientShowsFailsInsteadOfBeingCutSilently() {
        StringBuilder sb = new StringBuilder("&a&l");
        for (int i = 0; i < 513; i++) sb.append('x');
        sdkRejects("at most 512", () -> Panel.create("p", Place.chat()).content(Ui.text(sb.toString())).toJson());
        StringBuilder ok = new StringBuilder("&a&l&#FF00FF");
        for (int i = 0; i < 512; i++) ok.append('x');
        clientAccepts(Panel.create("p", Place.chat()).content(Ui.text(ok.toString())).toJson());
        JsonObject raw = json(Panel.create("p", Place.chat()).content(Ui.text("x")));
        rootChildren(raw).get(0).getAsJsonObject().addProperty("text", sb.toString());
        ClientSim sim = new ClientSim();
        sim.deliver(raw.toString().getBytes(StandardCharsets.UTF_8));
        sim.assertClean();
        assertEquals(512, sim.panel("p").root.children.get(0).text.length());
    }

    @Test
    void badgeAndCheckboxDefaultsMatchTheClient() {
        JsonElement plain = Badge.of("NEW").toJson(32, "badge");
        assertTrue(plain.isJsonPrimitive());
        JsonElement styled = Badge.of("NEW").pos(Badge.Pos.BOTTOM).toJson(32, "badge");
        assertTrue(styled.isJsonObject());
        assertEquals("bottom", styled.getAsJsonObject().get("pos").getAsString());
    }
}
