package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code menu.set} message: the server menu with its title and tabs. Fill the tabs with panels placed at
 * {@link Place#menu(String)}, and open the menu with {@link Session#openMenu(String)}. It holds at most
 * {@link Limits#MENU_TABS} tabs with unique ids, and each tab shows at most {@link Limits#PANELS_PER_TAB} panels.
 */
public final class Menu extends Message {

    /** Visual style for menu components or font. */
    public enum Look {
        /** The Leaf Client style. */
        LEAF,
        /** The Minecraft style. */
        MINECRAFT
    }

    private final Text title;
    private Text subtitle;
    private Icon icon;
    private String accent;
    private final List<Tab> tabs = new ArrayList<Tab>();
    private String defaultTab;
    private Look components;
    private Look font;

    private Menu(Text title) {
        this.title = title;
    }

    /**
     * Creates a menu.
     *
     * @param title legacy text with {@code &} or {@code §} codes, at most 48 visible characters
     * @return the new message
     * @throws InvalidUiException if the title is null
     */
    public static Menu create(String title) {
        return new Menu(Text.of(title));
    }

    /**
     * Creates a menu.
     *
     * @param title the title, at most 48 visible characters, or null to send none (the client then shows the server name)
     * @return the new message
     */
    public static Menu create(Text title) {
        return new Menu(title);
    }

    /**
     * Creates a tab to add with {@link #add(Tab...)}.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param label legacy text, at most 32 visible characters
     * @return the tab
     * @throws InvalidUiException if the id is not valid or the label is null
     */
    public static Tab tab(String id, String label) {
        return new Tab(id, Text.of(label));
    }

    /**
     * Creates a tab to add with {@link #add(Tab...)}.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param label the label, at most 32 visible characters, or null for none
     * @return the tab
     * @throws InvalidUiException if the id is not valid
     */
    public static Tab tab(String id, Text label) {
        return new Tab(id, label);
    }

    /**
     * Sets the subtitle.
     *
     * @param subtitle legacy text, at most 96 visible characters
     * @return this message
     * @throws InvalidUiException if the subtitle is null
     */
    public Menu subtitle(String subtitle) {
        return subtitle(Text.of(subtitle));
    }

    /**
     * Sets the subtitle.
     *
     * @param subtitle the subtitle, at most 96 visible characters, or null for none
     * @return this message
     */
    public Menu subtitle(Text subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    /**
     * Sets the menu icon.
     *
     * @param icon the icon, or null for none
     * @return this message
     */
    public Menu icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets the accent color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Menu accent(String color) {
        this.accent = Check.color(color, "menu.accent");
        return this;
    }

    /**
     * Adds tabs at the end.
     *
     * @param tabs the tabs; the menu holds at most {@link Limits#MENU_TABS}
     * @return this message
     * @throws InvalidUiException if a tab is null or there would be too many tabs
     */
    public Menu add(Tab... tabs) {
        for (Tab t : tabs) {
            if (this.tabs.size() >= Limits.MENU_TABS) throw Check.fail("menu.tabs", "more than " + Limits.MENU_TABS + " tabs (MENU_TABS)");
            this.tabs.add(Check.notNull(t, "menu.tabs"));
        }
        return this;
    }

    /**
     * Sets the tab the menu opens on.
     *
     * @param tabId the tab id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return this message
     * @throws InvalidUiException if the id is not valid
     */
    public Menu defaultTab(String tabId) {
        this.defaultTab = Check.id(tabId, "menu.default");
        return this;
    }

    /**
     * Sets the style of the menu components.
     *
     * @param look the style, or null for the client default
     * @return this message
     */
    public Menu components(Look look) {
        this.components = look;
        return this;
    }

    /**
     * Sets the font style of the menu.
     *
     * @param look the style, or null for the client default
     * @return this message
     */
    public Menu font(Look look) {
        this.font = look;
        return this;
    }

    @Override
    Outgoing encode(Session session) {
        JsonObject o = Json.envelope("menu.set");
        if (title != null) o.add("title", Check.text(title, 48, "menu.title").toJson());
        if (subtitle != null) o.add("subtitle", Check.text(subtitle, 96, "menu.subtitle").toJson());
        if (icon != null) o.add("icon", icon.toJson());
        Json.str(o, "accent", accent);
        Set<String> ids = new HashSet<String>();
        if (!tabs.isEmpty()) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                if (!ids.add(t.id)) throw Check.fail("menu.tabs[" + i + "].id", "duplicate tab id " + t.id);
                a.add(t.toJson("menu.tabs[" + i + "]"));
            }
            o.add("tabs", a);
        }
        Json.str(o, "default", defaultTab);
        if (components != null || font != null) {
            JsonObject style = new JsonObject();
            if (components != null) style.addProperty("components", Check.lower(components));
            if (font != null) style.addProperty("font", Check.lower(font));
            o.add("style", style);
        }
        return Outgoing.simple(o, "menu.set");
    }

    /**
     * One tab of the server menu. Create it with {@link Menu#tab(String, String)}.
     */
    public static final class Tab {
        private final String id;
        private final Text label;
        private Icon icon;
        private Integer order;
        private Badge badge;
        private Text pageTitle;
        private Text pageSubtitle;
        private Icon pageIcon;

        Tab(String id, Text label) {
            this.id = Check.id(id, "menu.tabs.id");
            this.label = label;
        }

        /**
         * Sets the tab icon.
         *
         * @param icon the icon, or null for none
         * @return this tab
         */
        public Tab icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Sets the sort order of the tab.
         *
         * @param order -1000 to 1000
         * @return this tab
         * @throws InvalidUiException if the order is out of range
         */
        public Tab order(int order) {
            this.order = Check.range(order, -1000, 1000, "menu.tabs.order");
            return this;
        }

        /**
         * Sets a text badge on the tab.
         *
         * @param badge legacy text, at most 16 visible characters
         * @return this tab
         * @throws InvalidUiException if the text is null
         */
        public Tab badge(String badge) {
            this.badge = Badge.of(badge);
            return this;
        }

        /**
         * Sets a badge on the tab. Its text shows at most 16 visible characters.
         *
         * @param badge the badge, or null for none
         * @return this tab
         */
        public Tab badge(Badge badge) {
            this.badge = badge;
            return this;
        }

        /**
         * Sets the page header of the tab. At least one of the two needs visible text.
         *
         * @param title legacy text, at most 64 visible characters, or null
         * @param subtitle legacy text, at most 160 visible characters, or null
         * @return this tab
         */
        public Tab page(String title, String subtitle) {
            return page(Text.from(title), Text.from(subtitle));
        }

        /**
         * Sets the page header of the tab. At least one of the two needs visible text.
         *
         * @param title the title, at most 64 visible characters, or null
         * @param subtitle the subtitle, at most 160 visible characters, or null
         * @return this tab
         */
        public Tab page(Text title, Text subtitle) {
            this.pageTitle = title;
            this.pageSubtitle = subtitle;
            return this;
        }

        /**
         * Sets the icon of the page header. A page header also needs a title or a subtitle.
         *
         * @param icon the icon, or null for none
         * @return this tab
         */
        public Tab pageIcon(Icon icon) {
            this.pageIcon = icon;
            return this;
        }

        JsonObject toJson(String at) {
            JsonObject t = new JsonObject();
            t.addProperty("id", id);
            if (label != null) t.add("label", Check.text(label, 32, at + ".label").toJson());
            if (icon != null) t.add("icon", icon.toJson());
            if (order != null) t.addProperty("order", order);
            if (badge != null) t.add("badge", badge.toJson(16, at + ".badge"));
            if (pageTitle != null || pageSubtitle != null || pageIcon != null) {
                JsonObject page = new JsonObject();
                if (pageTitle != null) page.add("title", Check.text(pageTitle, 64, at + ".page.title").toJson());
                if (pageSubtitle != null) page.add("subtitle", Check.text(pageSubtitle, 160, at + ".page.subtitle").toJson());
                if (pageIcon != null) page.add("icon", pageIcon.toJson());
                if ((pageTitle == null || pageTitle.visibleLength() == 0) && (pageSubtitle == null || pageSubtitle.visibleLength() == 0)) {
                    throw Check.fail(at + ".page", "a page needs a title or a subtitle");
                }
                t.add("page", page);
            }
            return t;
        }
    }
}
