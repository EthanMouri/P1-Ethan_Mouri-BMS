package com.revature.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Tracks inactivity for one authenticated customer session.
 */
public final class SessionManager {
    public static final Duration TIMEOUT = Duration.ofMinutes(5);
    private final Clock clock;
    private Instant lastActivity;

    /** Starts a five-minute session using the system clock. */
    public SessionManager() {
        this(Clock.systemUTC());
    }

    /** Starts a session with a supplied clock for deterministic tests. */
    public SessionManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastActivity = clock.instant();
    }

    /** Returns the time of the last accepted activity. */
    public Instant getLastActivity() {
        return lastActivity;
    }

    /** Returns true when five minutes or more have elapsed. */
    public boolean isExpired() {
        return !clock.instant().isBefore(lastActivity.plus(TIMEOUT));
    }

    /** Checks expiration before recording the next activity. */
    public void checkAndRefresh() {
        Instant now = clock.instant();
        if (!now.isBefore(lastActivity.plus(TIMEOUT))) {
            throw new SessionExpiredException();
        }
        //Only valid sessions may extend their inactivity deadline.
        lastActivity = now;
    }
}
