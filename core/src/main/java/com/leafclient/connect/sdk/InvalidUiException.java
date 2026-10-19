package com.leafclient.connect.sdk;

/**
 * Thrown when a builder value or a message breaks a Leaf Client limit or rule. The message names the field path and
 * the problem, for example {@code panel "shop" panel.root.children[2].label: 600 visible characters, the client shows at most 512}.
 * The SDK throws it before anything is sent, so the client never receives UI it would reject.
 */
public final class InvalidUiException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the field path and the problem
     */
    public InvalidUiException(String message) {
        super(message);
    }
}
