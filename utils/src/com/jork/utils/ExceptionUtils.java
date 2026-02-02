package com.jork.utils;

/**
 * Utility helpers for exception handling across scripts/utils.
 *
 * Important: OSMB's task pollers require TaskInterruptedException to bubble
 * up uncaught. Use rethrowIfTaskInterrupted(e) at the top of broad catch blocks
 * to ensure the host can interrupt blocking submitTask/submitHumanTask calls.
 */
public final class ExceptionUtils {

    private ExceptionUtils() {}

    /**
     * If the provided Throwable or any of its causes is a TaskInterruptedException,
     * rethrows it so that the OSMB host can handle the interruption properly.
     * This uses a type-name check to avoid hard coupling to the OSMB classpath.
     *
     * @param t the caught throwable
     */
    public static void rethrowIfTaskInterrupted(Throwable t) {
        if (t == null) return;
        // Walk the cause chain defensively (limit depth to avoid cycles)
        Throwable cursor = t;
        int depth = 0;
        while (cursor != null && depth++ < 16) {
            if ("TaskInterruptedException".equals(cursor.getClass().getSimpleName())) {
                sneakyThrow(cursor);
            }
            cursor = cursor.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
        throw (E) t; // Propagate the original exception without wrapping
    }
}

