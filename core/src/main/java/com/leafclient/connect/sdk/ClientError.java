package com.leafclient.connect.sdk;

/**
 * An {@code error} message from Leaf Client: the client could not use something the server sent.
 * Receive it with {@link LeafConnect#onError}. By default {@link LeafConnect} also logs it as a warning.
 */
public final class ClientError {

    private final Session session;
    private final String code;
    private final String ref;
    private final String detail;
    private final String sent;

    ClientError(Session session, String code, String ref, String detail, String sent) {
        this.session = session;
        this.code = code;
        this.ref = ref;
        this.detail = detail;
        this.sent = sent;
    }

    /**
     * Returns the session of the player whose client reported the error.
     *
     * @return the session
     */
    public Session session() {
        return session;
    }

    /**
     * Returns the error code the client sent.
     *
     * @return the code, or {@code "null"} if the client sent none
     */
    public String code() {
        return code;
    }

    /**
     * Returns the reference id of the frame the error is about, as the client sent it.
     *
     * @return the reference, or null
     */
    public String ref() {
        return ref;
    }

    /**
     * Returns the client's description of the problem.
     *
     * @return the detail, or null
     */
    public String detail() {
        return detail;
    }

    /**
     * Returns a description of what the SDK sent under {@link #ref()}, such as the labels of the messages in that batch.
     * The SDK remembers the last 256 references.
     *
     * @return the description, or null if the reference is unknown
     */
    public String sent() {
        return sent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Leaf client of ").append(session.name()).append(" reported ").append(code);
        if (detail != null) sb.append(": ").append(detail);
        if (sent != null) sb.append(" [in ").append(sent).append("]");
        else if (ref != null) sb.append(" [ref ").append(ref).append("]");
        return sb.toString();
    }
}
