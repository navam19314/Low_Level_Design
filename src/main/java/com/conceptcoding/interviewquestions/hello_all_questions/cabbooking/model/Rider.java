package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

import java.util.Objects;

/** A rider — identity. Location is supplied per request (pickup point). */
public class Rider {
    private final String id;
    private final String name;

    public Rider(String id, String name) {
        this.id   = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rider r)) return false;
        return id.equals(r.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "Rider(" + id + "," + name + ")"; }
}
