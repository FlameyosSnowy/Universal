package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class Logging {
    public static boolean ENABLED = false;
    public static boolean DEEP = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(Logging.class);

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void error(String string) {
        LOGGER.error(string);
    }

    public static void error(String string, Throwable throwable) {
        LOGGER.error(string, throwable);
    }

    public static void error(Throwable throwable) {
        LOGGER.error(throwable.getMessage(), throwable);
    }

    public static void info(String string) {
        if (ENABLED) LOGGER.info(string);
    }

    public static void deepInfo(String string) {
        if (ENABLED && DEEP) LOGGER.info(string);
    }
}
