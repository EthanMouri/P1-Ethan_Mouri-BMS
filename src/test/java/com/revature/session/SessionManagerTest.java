package com.revature.session;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the five-minute inactivity rules without sleeping. */
class SessionManagerTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final class AdjustableClock extends Clock {
        private Instant current = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
        void advance(Duration duration) { current = current.plus(duration); }
    }

    @Test void startsActive() {
        //Newly authenticated customers begin with a valid session.
        assertFalse(new SessionManager(new AdjustableClock()).isExpired());
    }

    @Test void startsAtCurrentTime() {
        assertEquals(START, new SessionManager(new AdjustableClock()).getLastActivity());
    }

    @Test void expiresAtExactlyFiveMinutes() {
        AdjustableClock clock = new AdjustableClock();
        SessionManager session = new SessionManager(clock);
        clock.advance(Duration.ofMinutes(5));
        assertTrue(session.isExpired());
    }

    @Test void staysActiveJustBeforeFiveMinutes() {
        AdjustableClock clock = new AdjustableClock();
        SessionManager session = new SessionManager(clock);
        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertFalse(session.isExpired());
    }

    @Test void acceptedActivityResetsDeadline() {
        AdjustableClock clock = new AdjustableClock();
        SessionManager session = new SessionManager(clock);
        clock.advance(Duration.ofMinutes(4));
        session.checkAndRefresh();
        clock.advance(Duration.ofMinutes(4));
        assertFalse(session.isExpired());
    }

    @Test void expiredActivityIsRejected() {
        AdjustableClock clock = new AdjustableClock();
        SessionManager session = new SessionManager(clock);
        clock.advance(Duration.ofMinutes(6));
        assertThrows(SessionExpiredException.class, session::checkAndRefresh);
    }

    @Test void rejectedActivityDoesNotResetTime() {
        AdjustableClock clock = new AdjustableClock();
        SessionManager session = new SessionManager(clock);
        clock.advance(Duration.ofMinutes(5));
        assertThrows(SessionExpiredException.class, session::checkAndRefresh);
        assertEquals(START, session.getLastActivity());
    }

    @Test void nullClockIsRejected() {
        assertThrows(NullPointerException.class, () -> new SessionManager(null));
    }
}
