package com.revature.session;

import org.jline.reader.LineReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Guards every authenticated CLI prompt without changing existing menu methods.
 */
public final class SessionLineReader {
    private SessionLineReader() { }

    /** Wraps a JLine reader with a five-minute inactivity check. */
    public static LineReader protect(LineReader reader, SessionManager session) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(session, "session");
        return (LineReader) Proxy.newProxyInstance(
                LineReader.class.getClassLoader(),
                new Class<?>[]{LineReader.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("readLine")) {
                        //Reject expired sessions before displaying the next prompt.
                        if (session.isExpired()) {
                            throw new SessionExpiredException();
                        }
                    }
                    try {
                        Object result = method.invoke(reader, args);
                        if (method.getName().equals("readLine")) {
                            //Also check after input: time spent idle at a prompt counts.
                            session.checkAndRefresh();
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        //Preserve JLine exceptions instead of wrapping them.
                        throw exception.getCause();
                    }
                });
    }
}
