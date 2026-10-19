package com.leafclient.connect.sdk;

import com.leafclient.connect.core.ConnectAction;
import com.leafclient.connect.core.ConnectBadge;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectIcon;
import com.leafclient.connect.core.ConnectPanel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveryElementTest {

    static final UUID A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID B = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    static final UUID C = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6");
    static final UUID D = UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19");

    static Panel everything() {
        Button fancy = Ui.button("fancy")
                .labelParts("&aBuy ", Icon.item("minecraft:emerald").size(10), Text.plain(" now").color("gold").bold())
                .icon(Icon.item("minecraft:diamond_sword").count(3).glint().size(20).tint("#80FFFFFF"))
                .iconPos(Button.IconPos.TOP)
                .shape(Shape.PILL)
                .radius(10)
                .labelScale(1.5)
                .size(Button.Size.WIDE)
                .bg("#1E2630").bgHover("#2F6B45").bgPressed("#123").bgDisabled("#333333").border("#8BE9A8").borderHover("#FFFFFF")
                .borderWidth(2).textColor("#FFFFFF").textHover("#FFFF55").textDisabled("#777777").glow("#8BE9A8")
                .tooltip("Line one", "&7Line {v:coins}")
                .disabledTooltip("Need 500 coins")
                .badge(Badge.of("NEW").icon(Icon.leaf("star")).bg("#E5484D").border("#FFFFFF").color("#FFFFFF").scale(1.5)
                        .textScale(0.8).iconSize(10).pad(4).shape(Shape.ROUND).pos(Badge.Pos.TOP_LEFT).offset(-3, 2))
                .sound("minecraft:block.note_block.pling")
                .key(3)
                .on(Action.send("buy:boots").confirm("Buy boots?", "500 coins"),
                        Action.command("/shop open"),
                        Action.waypoint("Shop", 10, 64, -20).dim("minecraft:overworld").color("#F5A524").icon(Icon.item("minecraft:chest")).save(),
                        Action.page(Action.Page.FRIENDS))
                .enabledWhen(Condition.all(Condition.ge("coins", 500), Condition.not(Condition.sneaking())))
                .cooldown(5000, Button.CooldownShow.BOTH)
                .cooldownLeft(1200);

        Container cards = Ui.grid(2,
                Ui.button("plain", "Plain"),
                Ui.button("off", "Off").enabled(false).badge("3"),
                Ui.button("open", "Open").on(Action.openMenu("shop")),
                Ui.button("front", "Front").on(Action.openPanel("vote")),
                Ui.button("close", "Close").on(Action.close()),
                Ui.button("close2", "Close vote").on(Action.close("vote")),
                Ui.button("url", "Site").on(Action.url("https://leafclient.com/store")),
                Ui.button("copy", "Copy").on(Action.copy("play.example.net")),
                Ui.button("suggest", "Msg").on(Action.suggest("/msg Steve ")),
                Ui.button("menu", "Menu").on(Action.openMenu()),
                Ui.button("silent", "Shh").silent().shape(Shape.CIRCLE).size(Button.Size.S)
        ).cell(60, 24).gap(2);

        Container layered = Ui.layer(
                Ui.image("myserver:textures/gui/banner.png").fit(Image.Fit.COVER).tint("#FFAAAAAA").size(120, 40),
                Ui.text("&lOverlay").at(6, 4).shadow()
        ).size(120, 40);

        Container styled = Ui.col(
                Ui.text("Styled card").size(TextElement.Size.L).color("#8BE9A8").maxLines(2).align(Align.CENTER),
                Ui.text(Text.plain("Scaled").italic()).scale(1.25),
                Ui.separator().thickness(2).color("#40FFFFFF"),
                Ui.space(4, 6),
                Ui.icon(Icon.head(B)).size(24),
                Ui.icon(Icon.sprite("myserver:gui/coin")),
                Ui.webImage("https://leafclient.com/banner.png").fit(Image.Fit.CONTAIN),
                Ui.player(C).yaw(-30).pitch(10),
                Ui.bar("{v:poll.a}", "{v:poll.total}").color("#2FA36B").bg("#202020").label("Votes").thickness(8),
                Ui.bar(30, 100),
                Ui.list("&aOne", "Two", "Three").visibleRows(2).color("#DDDDDD"),
                Ui.heads(A, B, C).size(12).gap(3),
                Ui.scroll(80, Ui.col(Ui.text("a"), Ui.text("b"), Ui.text("c")).gap(1))
        ).bg("#D0101418").bg2("#1B1E25").border("#40FFFFFF").borderWidth(2).radius(8)
                .accent(Container.Side.LEFT, "#8BE9A8", 3).pad(6).gap(5).justify(Justify.BETWEEN).grow(1);

        Leaderboard board = Ui.leaderboard().id("board")
                .row(A, "Steve", "1,200")
                .row(B, "Alex", "990")
                .row(C, "Notch", null)
                .row(D, "Jeb", "500")
                .podium(3).cardWidth(90).accent("#8BE9A8").placeColors("#FFD54F", "#C9D1DB", "#D9925B").width(300);

        Container form = Ui.col(
                Ui.input("name").label("Team name").placeholder("Leafers").value("Oak").maxLength(24).tooltip("Shown on the board"),
                Ui.toggle("public").label("Public").value(true),
                Ui.checkbox("rules").label("I read the rules"),
                Ui.choice("mode", "solo", "duo").option("squad", "Squad of 4").value("duo").label("Mode"),
                Ui.slider("bet", 0, 100).step(5).value(20).label("Bet").enabled(false)
        );

        Container top = Ui.row(fancy, Ui.text("wraps").width(40)).wrap().width(200).id("toprow");

        return Panel.create("all.elements", Place.menu("demo").side(Place.Side.LEFT).order(2))
                .size(400, 500).theme(Panel.Theme.DARK).pad(8).radius(10).bg("#E0101418").border("#40FFFFFF")
                .group("demo").scope(Panel.Scope.CONNECTION).z(5).ttl(60000)
                .when(Condition.any(Condition.eq("game.state", "waiting"), Condition.in("rank", "vip", "mvp", 3)))
                .root(Ui.col(top, cards, layered, styled, board, form).gap(6).align(Align.STRETCH));
    }

    @Test
    void clientAcceptsEveryElementAndOption() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        assertTrue(s.send(everything()));
        sim.flush();
        sim.assertClean();

        ConnectPanel p = sim.panel("all.elements");
        assertEquals("menu", p.placement);
        assertEquals("demo", p.place.tab);
        assertEquals(2, p.place.order);
        assertEquals(400, p.width);
        assertEquals(500, p.height);
        assertEquals(ConnectPanel.Theme.DARK, p.theme);
        assertEquals(8, p.pad);
        assertEquals(10, p.radius);
        assertEquals("demo", p.group);
        assertEquals(ConnectPanel.Scope.CONNECTION, p.scope);
        assertEquals(5, p.z);
        assertEquals(60000, p.ttlMs);
        assertNotNull(p.when);
        assertEquals(5, p.inputCount);
        assertEquals(1, p.scrollCount);

        ConnectElement fancy = sim.element("all.elements", "fancy");
        assertEquals(ConnectElement.Kind.BUTTON, fancy.kind);
        assertEquals(3, fancy.label.size());
        assertNotNull(fancy.label.get(1).icon);
        assertEquals(ConnectIcon.Kind.ITEM, fancy.icon.kind);
        assertEquals(3, fancy.icon.count);
        assertTrue(fancy.icon.glint);
        assertEquals(20, fancy.icon.size);
        assertEquals(ConnectElement.IconPos.TOP, fancy.iconPos);
        assertEquals(ConnectElement.Shape.PILL, fancy.shape);
        assertEquals(10, fancy.radius);
        assertEquals(1.5f, fancy.labelScale);
        assertEquals(100, fancy.w);
        assertEquals(22, fancy.h);
        assertEquals(Integer.valueOf(0xFF1E2630), fancy.bg);
        assertEquals(Integer.valueOf(0xFF112233), fancy.bgPressed);
        assertEquals(2, fancy.borderWidth);
        assertEquals(Integer.valueOf(0xFF8BE9A8), fancy.glow);
        assertEquals(2, fancy.tooltip.size());
        assertEquals(1, fancy.disabledTooltip.size());
        assertEquals("minecraft:block.note_block.pling", fancy.sound);
        assertEquals(3, fancy.key);
        assertEquals(5000, fancy.cooldownMs);
        assertEquals(ConnectElement.CooldownShow.BOTH, fancy.cooldownShow);
        assertNotNull(fancy.enabledWhen);

        ConnectBadge badge = fancy.badge;
        assertEquals(ConnectBadge.Pos.TOP_LEFT, badge.pos);
        assertEquals(ConnectElement.Shape.ROUND, badge.shape);
        assertEquals(1.5f, badge.scale);
        assertEquals(0.8f, badge.textScale, 1e-6);
        assertEquals(10, badge.iconSize);
        assertEquals(4, badge.pad);
        assertEquals(-3, badge.offsetX);
        assertEquals(2, badge.offsetY);
        assertEquals("NEW", badge.text.plain());
        assertNotNull(badge.icon);
        assertEquals(Integer.valueOf(0xFFFFFFFF), badge.border);

        assertEquals(4, fancy.on.size());
        assertEquals(ConnectAction.Do.SEND, fancy.on.get(0).kind);
        assertEquals("buy:boots", fancy.on.get(0).data);
        assertEquals("Buy boots?", fancy.on.get(0).confirm.title.plain());
        assertEquals("500 coins", fancy.on.get(0).confirm.body.plain());
        assertEquals(ConnectAction.Do.COMMAND, fancy.on.get(1).kind);
        assertEquals(ConnectAction.Do.WAYPOINT, fancy.on.get(2).kind);
        assertTrue(fancy.on.get(2).waypoint.save);
        assertEquals("minecraft:overworld", fancy.on.get(2).waypoint.dim);
        assertEquals(ConnectAction.Do.PAGE, fancy.on.get(3).kind);
        assertEquals("friends", fancy.on.get(3).value);

        ConnectBadge plainBadge = sim.element("all.elements", "off").badge;
        assertEquals(ConnectBadge.Pos.TOP_RIGHT, plainBadge.pos);
        assertEquals(ConnectBadge.DEFAULT_BG, plainBadge.bg);
        assertFalse(sim.element("all.elements", "off").enabled);
        assertEquals("shop", sim.element("all.elements", "open").on.get(0).menuTab);
        assertEquals("vote", sim.element("all.elements", "front").on.get(0).panel);
        assertEquals(ConnectAction.Do.CLOSE, sim.element("all.elements", "close").on.get(0).kind);
        assertEquals(ConnectAction.Do.URL, sim.element("all.elements", "url").on.get(0).kind);
        assertEquals(ConnectAction.Do.COPY, sim.element("all.elements", "copy").on.get(0).kind);
        assertEquals(ConnectAction.Do.SUGGEST, sim.element("all.elements", "suggest").on.get(0).kind);
        assertEquals("", sim.element("all.elements", "menu").on.get(0).menuTab);
        assertEquals("none", sim.element("all.elements", "silent").sound);
        assertEquals(ConnectElement.Shape.CIRCLE, sim.element("all.elements", "silent").shape);
        assertEquals(24, sim.element("all.elements", "silent").w);
        assertEquals(ConnectAction.DEFAULT, sim.element("all.elements", "plain").on);

        ConnectElement toggle = sim.element("all.elements", "public");
        ConnectElement checkbox = sim.element("all.elements", "rules");
        assertEquals(ConnectElement.Kind.TOGGLE, toggle.kind);
        assertFalse(toggle.checkbox);
        assertTrue(toggle.initialOn);
        assertEquals(ConnectElement.Kind.TOGGLE, checkbox.kind);
        assertTrue(checkbox.checkbox);
        assertFalse(checkbox.initialOn);

        ConnectElement input = sim.element("all.elements", "name");
        assertEquals(ConnectElement.Kind.INPUT, input.kind);
        assertEquals(24, input.maxChars);
        assertEquals("Oak", input.initialText);
        assertEquals("Leafers", input.placeholder.plain());

        ConnectElement choice = sim.element("all.elements", "mode");
        assertEquals(3, choice.optionIds.size());
        assertEquals("duo", choice.initialChoice);
        assertEquals("Squad of 4", choice.optionLabels.get(2).plain());

        ConnectElement slider = sim.element("all.elements", "bet");
        assertEquals(0, slider.min);
        assertEquals(100, slider.max);
        assertEquals(5, slider.step);
        assertEquals(20, slider.initialNumber);
        assertFalse(slider.enabled);

        ConnectElement board = sim.element("all.elements", "board");
        assertEquals(ConnectElement.Kind.COL, board.kind);
        assertEquals(300, board.w);
        assertEquals(2, board.children.size());
        ConnectElement stage = board.children.get(0);
        assertEquals(ConnectElement.Kind.ROW, stage.kind);
        assertEquals(3, stage.children.size());
        assertEquals(ConnectElement.Kind.LAYER, stage.children.get(0).kind);
        assertEquals(90, stage.children.get(0).w);
        ConnectElement rest = board.children.get(1);
        assertEquals(1, rest.children.size());
        assertEquals(ConnectElement.Kind.HEADS, rest.children.get(0).children.get(1).kind);

        ConnectElement toprow = sim.element("all.elements", "toprow");
        assertTrue(toprow.wrap);
        assertEquals(200, toprow.w);

        ConnectElement styled = p.root.children.get(3);
        assertEquals(Integer.valueOf(0xD0101418), styled.bg);
        assertEquals(Integer.valueOf(0xFF1B1E25), styled.bg2);
        assertEquals(2, styled.borderWidth);
        assertEquals(8, styled.radius);
        assertEquals(ConnectElement.Side.LEFT, styled.accentSide);
        assertEquals(Integer.valueOf(0xFF8BE9A8), styled.accentColor);
        assertEquals(3, styled.accentWidth);
        assertEquals(ConnectElement.Justify.BETWEEN, styled.justify);
        assertEquals(5, styled.gap);
        assertEquals(1f, styled.grow);
        assertEquals(ConnectElement.Kind.TEXT, styled.children.get(0).kind);
        assertEquals(1.5f, styled.children.get(0).textScale);
        assertEquals(2, styled.children.get(0).maxLines);
        assertEquals(1.25f, styled.children.get(1).textScale);
        assertEquals(2, styled.children.get(2).thickness);
        assertEquals(ConnectElement.Kind.SPACE, styled.children.get(3).kind);
        assertEquals(24, styled.children.get(4).w);
        assertEquals(ConnectIcon.Kind.HEAD, styled.children.get(4).icon.kind);
        assertEquals(ConnectIcon.Kind.SPRITE, styled.children.get(5).icon.kind);
        assertEquals("https://leafclient.com/banner.png", styled.children.get(6).imageUrl);
        assertEquals(ConnectElement.Fit.CONTAIN, styled.children.get(6).fit);
        assertEquals(C.toString(), styled.children.get(7).player);
        assertEquals(-30f, styled.children.get(7).yaw);
        assertEquals("{v:poll.a}", styled.children.get(8).barValue);
        assertEquals(8, styled.children.get(8).barHeight);
        assertEquals("30", styled.children.get(9).barValue);
        assertEquals(3, styled.children.get(10).rows.size());
        assertEquals(2, styled.children.get(10).visibleRows);
        assertEquals(3, styled.children.get(11).players.size());
        assertEquals(12, styled.children.get(11).headSize);
        assertEquals(ConnectElement.Kind.SCROLL, styled.children.get(12).kind);
        assertEquals(80, styled.children.get(12).h);

        ConnectElement layered = p.root.children.get(2);
        assertEquals(ConnectElement.Kind.LAYER, layered.kind);
        assertEquals("myserver:textures/gui/banner.png", layered.children.get(0).texture);
        assertEquals(6, layered.children.get(1).atX);
        assertTrue(layered.children.get(1).shadow);

        ConnectElement grid = p.root.children.get(1);
        assertEquals(2, grid.cols);
        assertEquals(60, grid.cellW);
        assertNull(grid.bg);
    }

    @Test
    void everyPlacementWithItsOptions() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.sendAll(
                Panel.create("p.inv", Place.inventory(Place.Side.RIGHT).align(Place.VAlign.CENTER).order(1)).content(Ui.button("b", "B")),
                Panel.create("p.chat", Place.chat().order(-1)).content(Ui.button("b", "B")),
                Panel.create("p.front", Place.front().distance(2.5).height(0.3).face(Place.Face.BILLBOARD).closeOnKey(false).closeOnWalk(5)
                        .closeAfter(30000).closeOnClick().yaw(10).pitch(5).roll(2).scale(0.02).reach(5).blockAttack(false)).content(Ui.button("b", "B")),
                Panel.create("p.block", Place.block(10, 64, -5).offset(0.5, 2.2, 0.5).mount(Place.Mount.NORTH).view(32).throughWalls()
                        .world("hub").dim("minecraft:overworld").stack("npc1").scale(0.015).reach(8)).content(Ui.button("b", "B")),
                Panel.create("p.entity", Place.entity(42).offset(0, 2, 0).face(Place.Face.FIXED).yaw(90)).content(Ui.button("b", "B")),
                Panel.create("p.player", Place.player(B).face(Place.Face.BILLBOARD)).content(Ui.button("b", "B")),
                Panel.create("p.hud", Place.hud(Place.Anchor.BOTTOM_LEFT)).content(Ui.text("{t:round:mm:ss}")),
                Panel.create("p.container", Place.container(Place.Side.LEFT).title("Leaf Shop").align(Place.VAlign.BOTTOM)).content(Ui.button("b", "B")),
                Panel.create("p.menu", Place.menu().side(Place.Side.RIGHT)).content(Ui.button("b", "B")));
        sim.flush();
        sim.assertClean();
        assertTrue(sim.panel("p.inv").place.rightSide);
        assertEquals("center", sim.panel("p.inv").place.align);
        assertEquals(-1, sim.panel("p.chat").place.order);
        ConnectPanel front = sim.panel("p.front");
        assertEquals(2.5, front.place.dist);
        assertFalse(front.place.closeOnKey);
        assertEquals(5, front.place.closeWalk);
        assertEquals(30000, front.place.closeAfterMs);
        assertTrue(front.place.closeOnClick);
        assertFalse(front.place.blockAttack);
        assertEquals(0.02, front.place.scale);
        ConnectPanel block = sim.panel("p.block");
        assertEquals(10, block.place.pos[0]);
        assertEquals(-5, block.place.pos[2]);
        assertEquals(2.2, block.place.offset[1]);
        assertEquals("NORTH", block.place.mount.name());
        assertEquals("FIXED", block.place.face.name());
        assertTrue(block.place.throughWalls);
        assertEquals("hub", block.place.world);
        assertEquals("npc1", block.place.stack);
        assertEquals(32, block.place.view);
        assertEquals(42, sim.panel("p.entity").place.entityId);
        assertTrue(sim.panel("p.entity").place.entityOffset);
        assertEquals(B.toString(), sim.panel("p.player").place.player);
        assertEquals("BOTTOM_LEFT", sim.panel("p.hud").place.anchor.name());
        assertEquals("leaf shop", sim.panel("p.container").place.title);
        assertTrue(sim.panel("p.menu").place.rightSide);
        assertEquals(1, sim.frameCount);
    }
}
