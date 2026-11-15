package com.revature.util;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * Uses JLine to connect to the terminal
 */
public class TerminalConnector {

    //Don't allow class to be instantiated because this is a util class
    private TerminalConnector() {}

    //Connect to the terminal with JLine
    public static Terminal connectToCLI() throws IOException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        //determine if it is a dummy terminal of a JLine terminal
        //System.out.println("Terminal type: " + terminal.getType());
        return terminal;
    }

    //Use the JLine terminal to get a lineReader (parses System.in)
    public static LineReader createLineReader(Terminal terminal) {
        return LineReaderBuilder.builder().terminal(terminal).build();
    }
}

