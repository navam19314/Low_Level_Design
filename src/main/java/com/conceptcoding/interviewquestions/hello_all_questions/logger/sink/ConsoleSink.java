package com.conceptcoding.interviewquestions.hello_all_questions.logger.sink;

import java.io.PrintStream;

/** Writes to stdout. PrintStream is itself synchronized, but we still take the
 *  per-destination lock at the Destination layer so failure isolation + ordering
 *  invariants hold uniformly across sink types. */
public class ConsoleSink implements Sink {

    private final PrintStream out;

    public ConsoleSink()                 { this(System.out); }
    public ConsoleSink(PrintStream out)  { this.out = out; }

    @Override
    public void write(String formatted) {
        out.println(formatted);
    }
}
