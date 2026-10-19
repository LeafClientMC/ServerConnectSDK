package com.leafclient.connect.sdk;

/**
 * A message the server sends to Leaf Client with {@link Session#send(Message)}. Each subclass is a builder for one
 * message type. A message is checked against the client limits when it is encoded, and a broken one throws
 * {@link InvalidUiException}.
 */
public abstract class Message {

    Message() {}

    abstract Outgoing encode(Session session);

    String requiredCap() {
        return null;
    }

    /**
     * Encodes the message without a session, for previews and tests. Click tokens are the placeholder
     * {@code "preview"}, and limits that depend on what the session already holds are not checked.
     *
     * @return the message as a JSON string
     * @throws InvalidUiException if the message breaks a client limit
     */
    public String toJson() {
        return Json.write(encode(null).json);
    }

    /**
     * Returns the same text as {@link #toJson()}.
     *
     * @return the message as a JSON string
     * @throws InvalidUiException if the message breaks a client limit
     */
    @Override
    public String toString() {
        return toJson();
    }
}
