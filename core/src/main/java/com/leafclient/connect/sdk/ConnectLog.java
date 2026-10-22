package com.leafclient.connect.sdk;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where the SDK writes its log lines. The platform adapters pass one built from the plugin logger.
 */
public interface ConnectLog {

    /**
     * Logs an informational line.
     *
     * @param message the line
     */
    void info(String message);

    /**
     * Logs a warning, such as a client error or an unsupported protocol version.
     *
     * @param message the line
     */
    void warn(String message);

    /**
     * Logs a debug line, such as an ignored message, a message not sent because the client lacks a cap, or a rejected click.
     *
     * @param message the line
     */
    void debug(String message);

    /**
     * Logs a failure with its cause, such as a listener or click handler that threw. The default calls
     * {@link #warn(String)} with the message and the error.
     *
     * @param message the line
     * @param error the cause
     */
    default void error(String message, Throwable error) {
        warn(message + ": " + error);
    }

    /**
     * Tells whether debug lines are written. The SDK skips building debug lines when this is false. The default is
     * true.
     *
     * @return true when {@link #debug(String)} writes anything
     */
    default boolean debugEnabled() {
        return true;
    }

    /**
     * Wraps a {@code java.util.logging} logger. Debug lines go to {@link Level#FINE} and errors to {@link Level#SEVERE}.
     *
     * @param logger the logger
     * @return a log that writes to it
     */
    static ConnectLog of(final Logger logger) {
        return new ConnectLog() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warning(message);
            }

            @Override
            public void debug(String message) {
                logger.log(Level.FINE, message);
            }

            @Override
            public boolean debugEnabled() {
                return logger.isLoggable(Level.FINE);
            }

            @Override
            public void error(String message, Throwable error) {
                logger.log(Level.SEVERE, message, error);
            }
        };
    }

    /**
     * Returns a log that drops every line.
     *
     * @return the silent log
     */
    static ConnectLog silent() {
        return new ConnectLog() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public boolean debugEnabled() {
                return false;
            }
        };
    }
}
