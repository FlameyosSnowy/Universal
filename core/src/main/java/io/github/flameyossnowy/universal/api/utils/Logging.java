package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

@ApiStatus.Internal
public final class Logging {
    public static boolean ENABLED = false;
    public static boolean DEEP = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(Logging.class);
    private static final java.util.logging.Logger FALLBACK = java.util.logging.Logger.getLogger(Logging.class.getName());

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void error(String string) {
        if (LOGGER != NOPLogger.NOP_LOGGER) LOGGER.error(string);
        else FALLBACK.severe(string);

    }

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     * @param throwable the throwable that caused the error
     */
    public static void error(String string, Throwable throwable) {
        if (LOGGER != NOPLogger.NOP_LOGGER) LOGGER.error(string, throwable);
        else FALLBACK.log(java.util.logging.Level.SEVERE, string, throwable);
    }

    /**
     * Debugs an error but does not require debugging to be enabled.
     * @param throwable the throwable that caused the error
     */
    public static void error(Throwable throwable) {
        if (LOGGER != NOPLogger.NOP_LOGGER) LOGGER.error(throwable.getMessage(), throwable);
        else FALLBACK.log(java.util.logging.Level.SEVERE, throwable.getMessage(), throwable);
    }

    /**
     * Logs an info message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void info(String string) {
        if (ENABLED) {
            if (LOGGER != NOPLogger.NOP_LOGGER) LOGGER.info(string);
            else FALLBACK.info(string);
        }
    }

    /**
     * Logs an info message only if both {@link #ENABLED} and {@link #DEEP} are enabled.
     * @param string the message
     */
    public static void deepInfo(String string) {
        if (ENABLED && DEEP) {
            if (LOGGER != NOPLogger.NOP_LOGGER) LOGGER.info(string);
            else FALLBACK.info(string);
        }
    }
}
