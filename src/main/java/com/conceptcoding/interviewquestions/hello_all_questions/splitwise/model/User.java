package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

import java.util.Objects;

/** Immutable user record. id is the identity key used throughout the balance graph. */
public record User(String id, String name, String email) {
    public User {
        Objects.requireNonNull(id, "user id required");
        Objects.requireNonNull(name, "user name required");
    }
}
