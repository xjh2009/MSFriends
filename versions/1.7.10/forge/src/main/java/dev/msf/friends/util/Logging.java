package dev.msf.friends.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized logger factory for MSF Friends (1.7.10 — Log4j direct, no SLF4J).
 */
public final class Logging {
    private Logging() {}

    public static Logger get() {
        // Walk the call stack to find the caller's class name.
        // This gives us per-class loggers without each class needing to declare one.
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // [0]=getStackTrace, [1]=get(), [2]=caller
        String callerName = stack.length > 2 ? stack[2].getClassName() : "MSF";
        return LogManager.getLogger(callerName);
    }

    public static Logger get(Class<?> clazz) {
        return LogManager.getLogger(clazz);
    }
}
