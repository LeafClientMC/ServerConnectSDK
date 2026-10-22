package com.leafclient.connect.example;

import com.leafclient.connect.sdk.Action;
import com.leafclient.connect.sdk.Button;
import com.leafclient.connect.sdk.Click;
import com.leafclient.connect.sdk.Condition;
import com.leafclient.connect.sdk.Icon;
import com.leafclient.connect.sdk.Leaderboard;
import com.leafclient.connect.sdk.Menu;
import com.leafclient.connect.sdk.Panel;
import com.leafclient.connect.sdk.Place;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Toast;
import com.leafclient.connect.sdk.Ui;
import com.leafclient.connect.sdk.paper.PaperConnect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example Paper plugin for the Leaf Server Connect SDK. It also runs on Folia, Spigot and Bukkit. When a Leaf Client
 * player is ready it sends a menu with a shop, a leaderboard and a sign up form, a coins value, and a block panel at
 * the world spawn. The coins map is concurrent because on Folia each player's handlers run on their own region thread.
 */
public final class ExamplePlugin extends JavaPlugin {

    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private PaperConnect connect;

    /**
     * Starts the SDK and sends the UI to each player whose client says hello.
     */
    @Override
    public void onEnable() {
        connect = PaperConnect.enable(this);
        connect.onReady(this::sendUi);
    }

    /**
     * Stops the SDK.
     */
    @Override
    public void onDisable() {
        connect.disable();
    }

    private void sendUi(Session s) {
        Player player = s.player();
        s.send(Menu.create("Example Network").subtitle("{n:coins} coins").icon(Icon.item("minecraft:emerald")).accent("#8BE9A8")
                .add(Menu.tab("shop", "Shop").icon(Icon.leaf("shop")), Menu.tab("top", "Top").icon(Icon.item("minecraft:golden_apple")),
                        Menu.tab("join", "Sign up").icon(Icon.leaf("users"))));
        s.setValue("coins", coins(player.getUniqueId()));
        s.send(Panel.create("shop", Place.menu("shop")).root(Ui.grid(3,
                item("boots", "Speed Boots", "minecraft:diamond_boots", 500),
                item("bow", "Power Bow", "minecraft:bow", 300),
                item("apple", "Golden Apple", "minecraft:golden_apple", 100)).cell(96, 56).gap(6)));
        Leaderboard board = Ui.leaderboard();
        List<Player> online = new ArrayList<>(getServer().getOnlinePlayers());
        online.sort((a, b) -> coins(b.getUniqueId()) - coins(a.getUniqueId()));
        for (Player p : online.subList(0, Math.min(10, online.size()))) board.row(p.getUniqueId(), p.getName(), coins(p.getUniqueId()) + " coins");
        s.send(Panel.create("top", Place.menu("top")).content(board));
        s.send(Panel.create("join", Place.menu("join")).content(
                Ui.input("team").label("Team name").placeholder("Leafers"),
                Ui.choice("mode", "solo", "duo", "squad").label("Mode"),
                Ui.checkbox("rules").label("I read the rules"),
                Ui.button("submit", "Sign up").onClick(this::signUp)));
        Location spawn = player.getWorld().getSpawnLocation();
        s.send(Panel.create("spawn", Place.block(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()).offset(0.5, 2.5, 0.5).reach(8))
                .content(Ui.text("&lWelcome!"), Ui.button("open", "Open the shop").on(Action.openMenu("shop"))));
    }

    private Button item(String id, String name, String icon, int price) {
        return Ui.button(id, name + "\n&7" + price + " coins").icon(Icon.item(icon)).iconPos(Button.IconPos.TOP).size(96, 56)
                .enabledWhen(Condition.ge("coins", price)).disabledTooltip("You need " + price + " coins")
                .on(Action.send().confirm("Buy " + name + "?", price + " coins"))
                .onClick(click -> buy(click, name, price));
    }

    private void buy(Click click, String name, int price) {
        UUID id = click.session().id();
        if (coins(id) < price) return;
        coins.put(id, coins(id) - price);
        click.session().setValue("coins", coins(id)).send(Toast.create("bought").title("Bought " + name).kind(Toast.Kind.SUCCESS));
    }

    private void signUp(Click click) {
        if (!click.inputs().isOn("rules")) {
            click.session().send(Toast.create("signup").title("Read the rules first").kind(Toast.Kind.WARN));
            return;
        }
        String team = click.inputs().text("team").isEmpty() ? "Leafers" : click.inputs().text("team");
        click.session().send(Toast.create("signup").title("Signed up").body(team + ", " + click.inputs().choice("mode")).kind(Toast.Kind.SUCCESS));
    }

    private int coins(UUID id) {
        return coins.computeIfAbsent(id, k -> 1000);
    }
}
