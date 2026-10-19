package com.leafclient.connect.sdk;

/**
 * A click from Leaf Client that passed every SDK check: known panel, toast or waypoint, matching token, known and
 * enabled button, cooldown, valid inputs and, for world panels, reach. It goes to the button handler first, then to
 * every {@link LeafConnect#onClick} listener.
 */
public final class Click {

    private final Session session;
    private final String source;
    private final String panel;
    private final String button;
    private final String data;
    private final boolean confirmed;
    private final long seq;
    private final FormValues inputs;
    private final WorldAnchor anchor;

    Click(Session session, String source, String panel, String button, String data, boolean confirmed, long seq,
          FormValues inputs, WorldAnchor anchor) {
        this.session = session;
        this.source = source;
        this.panel = panel;
        this.button = button;
        this.data = data;
        this.confirmed = confirmed;
        this.seq = seq;
        this.inputs = inputs;
        this.anchor = anchor;
    }

    /**
     * Returns the session of the player who clicked.
     *
     * @return the session
     */
    public Session session() {
        return session;
    }

    /**
     * Returns the platform player object of the session, such as a Bukkit {@code Player}.
     *
     * @param <P> the platform player type
     * @return the player
     * @throws ClassCastException if {@code P} is not the platform player type
     */
    public <P> P player() {
        return session.player();
    }

    /**
     * Returns where the click came from: the place of the panel ({@code menu}, {@code inventory}, {@code container},
     * {@code chat}, {@code front}, {@code block}, {@code entity} or {@code hud}), {@code toast} or {@code waypoint}.
     *
     * @return the source
     */
    public String source() {
        return source;
    }

    /**
     * Returns the id of the panel, toast or waypoint that was clicked.
     *
     * @return the id
     */
    public String panel() {
        return panel;
    }

    /**
     * Returns the id of the button that was clicked. For a waypoint it is {@code label}.
     *
     * @return the button id
     */
    public String button() {
        return button;
    }

    /**
     * Tells whether this click is on a given panel and button.
     *
     * @param panelId the panel, toast or waypoint id
     * @param buttonId the button id
     * @return true if both match
     */
    public boolean is(String panelId, String buttonId) {
        return panel.equals(panelId) && button.equals(buttonId);
    }

    /**
     * Returns the data of the {@link Action#send(String)} action that sent the click.
     *
     * @return the data, or null
     */
    public String data() {
        return data;
    }

    /**
     * Tells whether the client reports that the player accepted a confirm dialog for this click.
     *
     * @return the {@code confirmed} flag of the click
     */
    public boolean confirmed() {
        return confirmed;
    }

    /**
     * Returns the sequence number of the click. It rises with every click from this client.
     *
     * @return the sequence number
     */
    public long seq() {
        return seq;
    }

    /**
     * Returns the values of the panel inputs. It is empty for panels without inputs, toasts and waypoints.
     *
     * @return the input values
     */
    public FormValues inputs() {
        return inputs;
    }

    /**
     * Returns where the clicked block or entity panel is in the world.
     *
     * @return the anchor, or null if the panel is not a block or entity panel
     */
    public WorldAnchor anchor() {
        return anchor;
    }

    @Override
    public String toString() {
        return "click " + source + " " + panel + "/" + button + " seq=" + seq + (data != null ? " data=" + data : "")
                + (inputs.isEmpty() ? "" : " inputs=" + inputs);
    }
}
