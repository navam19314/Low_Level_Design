package com.conceptcoding.interviewquestions.hello_all_questions.policies.urlshortener;

import java.util.function.Predicate;

// Strategy interface — how to mint the next unique id for a new short code.
//
// Two real approaches:
//
// Counter — atomic incrementing long. Guaranteed unique, predictable codes
// (each new code is one more than the last), but enumerable from outside.
// Random + retry — generate random long, retry if collision. Unpredictable
// codes, but probabilistically O(1) at low load factor.
//
//
// The isAvailable predicate lets impls test for collisions against the
// caller's map without coupling the Strategy to the storage layer.
public interface IdGenerationStrategy {

    // Generate a unique id whose base-62 encoding is NOT already taken.
    // isAvailable callback: "try to claim this base-62 code; true = it's yours"
    long nextId(Predicate<String> isAvailable);
}
