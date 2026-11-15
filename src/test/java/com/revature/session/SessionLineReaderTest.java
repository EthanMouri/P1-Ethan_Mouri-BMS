package com.revature.session;

import org.jline.reader.LineReader;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Tests that authenticated CLI input is checked before processing. */
class SessionLineReaderTest {
    private LineReader stub(AtomicInteger calls) {
        return (LineReader) Proxy.newProxyInstance(LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class}, (proxy, method, args) -> {
                    if (method.getName().equals("readLine")) {
                        calls.incrementAndGet();
                        return "1";
                    }
                    return null;
                });
    }

    @Test void validInputIsReturned() {
        AtomicInteger calls = new AtomicInteger();
        LineReader reader = SessionLineReader.protect(stub(calls), new SessionManager());
        assertEquals("1", reader.readLine("Choose: "));
        assertEquals(1, calls.get());
    }

    @Test void expiredSessionNeverReadsAnotherAction() {
        AtomicInteger calls = new AtomicInteger();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        //The session's clock must advance, so use a clock that returns a mutable instant.
        final Instant[] now = {start};
        Clock moving = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        SessionManager session = new SessionManager(moving);
        LineReader reader = SessionLineReader.protect(stub(calls), session);
        now[0] = start.plus(Duration.ofMinutes(5));
        assertThrows(SessionExpiredException.class, () -> reader.readLine("Choose: "));
        assertEquals(0, calls.get());
    }
}
