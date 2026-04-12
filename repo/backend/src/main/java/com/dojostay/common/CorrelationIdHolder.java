package com.dojostay.common;

/**
 * Thread-local accessor for the current request correlation id.
 *
 * <p>Populated by {@link CorrelationIdFilter} at the start of each request and cleared on
 * completion. Services and audit logging code use this to stamp records with the trace id
 * without having to thread it through method signatures everywhere.
 */
public final class CorrelationIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String value) {
        HOLDER.set(value);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
