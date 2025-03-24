package io.github.flameyossnowy.universal.api.utils;

import java.util.logging.*;

public final class Logging {
    public static boolean ENABLED = false;
    public static boolean DEEP = false;

    private static final Logger LOGGER = Logger.getLogger("Universal Library Debugger");

    private static final Handler[] HANDLERS;

    static {
        HANDLERS = LOGGER.getHandlers();
    }

    public static void simplify() {
        for (Handler handler : HANDLERS) {
            LOGGER.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();  // Only show the log message
            }
        });

        LOGGER.addHandler(consoleHandler);
        LOGGER.setUseParentHandlers(false); // Prevent duplicate logs
    }

    public static void addHandler(Handler handler) {
        LOGGER.addHandler(handler);
    }

    public static void removeHandler(Handler handler) {
        LOGGER.removeHandler(handler);
    }

    /**
     * Debugs an error message but does not require debugging to be enabled.
     * @param string the message
     */
    public static void error(String string) {
        LOGGER.severe(string);
    }

    public static void error(String string, Throwable throwable) {
        LOGGER.log(Level.SEVERE, string, throwable);
    }

    public static void error(Throwable throwable) {
        LOGGER.log(Level.SEVERE, throwable.getMessage(), throwable);
    }

    public static void info(String string) {
        if (ENABLED) LOGGER.info(string);
    }

    public static void deepInfo(String string) {
        if (ENABLED && DEEP) LOGGER.info(string);
    }
}
