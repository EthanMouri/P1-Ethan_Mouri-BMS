package com.revature.session;

/** Signals that an authenticated CLI session has expired. */
public final class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
        super("Session expired due to inactivity.");
    }
}
