package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.function.Supplier;
import java.util.logging.Level;

@ApiStatus.Internal
public final class Logging {
    public static boolean ENABLED = false;
    public static boolean DEEP = false;

    private static final Logger LOGGER;
    private static final java.util.logging.Logger FALLBACK;

    static {
        Logger detected;
        try {
            Logger logger = LoggerFactory.getLogger(Logging.class);
            detected = logger instanceof NOPLogger ? null : logger;
        } catch (NoClassDefFoundError e) {
            detected = null;
        }
        LOGGER = detected;

        if (LOGGER == null) {
            FALLBACK = java.util.logging.Logger.getLogger(Logging.class.getName());
        } else {
            FALLBACK = null;
        }
    }

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void error(String string) {
        if (LOGGER != null) LOGGER.error(string);
        else FALLBACK.severe(string);
    }

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void error(String string, Exception exception) {
        if (LOGGER != null) LOGGER.error(string, exception);
        else FALLBACK.log(Level.SEVERE, string, exception);
    }

    public static void warn(String string) {
        if (LOGGER != null) LOGGER.warn(string);
        else FALLBACK.warning(string);
    }

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     * @param throwable the throwable that caused the error
     */
    public static void error(String string, Throwable throwable) {
        if (LOGGER != null) LOGGER.error(string, throwable);
        else FALLBACK.log(java.util.logging.Level.SEVERE, string, throwable);
    }

    /**
     * Debugs an error but does not require debugging to be enabled.
     * @param throwable the throwable that caused the error
     */
    public static void error(Throwable throwable) {
        if (LOGGER != null) LOGGER.error(throwable.getMessage(), throwable);
        else FALLBACK.log(java.util.logging.Level.SEVERE, throwable.getMessage(), throwable);
    }

    /**
     * Logs an info message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void info(String string) {
        if (ENABLED) {
            if (LOGGER != null) LOGGER.info(string);
            else FALLBACK.info(string);
        }
    }

    /**
     * Logs an info message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void info(Supplier<String> string) {
        if (ENABLED) {
            if (LOGGER != null) LOGGER.info(string.get());
            else FALLBACK.info(string);
        }
    }

    /**
     * Logs an info message only if both {@link #ENABLED} and {@link #DEEP} are enabled.
     * @param string the message
     */
    public static void deepInfo(String string) {
        if (DEEP) {
            if (LOGGER != null) LOGGER.info(string);
            else FALLBACK.info(string);
        }
    }
    /**
     * Logs an info message only if both {@link #ENABLED} and {@link #DEEP} are enabled.
     * @param string the message
     */
    public static void deepInfo(Supplier<String> string) {
        if (DEEP) {
            if (LOGGER != null) LOGGER.info(string.get());
            else FALLBACK.info(string);
        }
    }
}
