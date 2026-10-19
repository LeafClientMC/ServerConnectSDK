package com.leafclient.connect.sdk;

/**
 * Sends raw frames to one player on the {@link LeafConnect#CHANNEL} plugin channel.
 * The platform adapters implement it. A custom platform passes its own to {@link LeafConnect#receive}.
 */
public interface Transport {

    /**
     * Sends one frame to the player.
     *
     * @param data the UTF-8 JSON frame, at most {@link #maxMessageBytes()} bytes
     */
    void send(byte[] data);

    /**
     * Returns the largest plugin message this platform can send. The session uses the smaller of this value and
     * {@link Limits#SERVER_MESSAGE_BYTES}, and splits larger messages into chunks.
     *
     * @return the maximum frame size in bytes
     */
    int maxMessageBytes();
}
