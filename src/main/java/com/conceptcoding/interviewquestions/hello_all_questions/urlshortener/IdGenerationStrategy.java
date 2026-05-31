package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

import java.util.function.Predicate;

/**
 * Strategy interface — how to mint the next unique id for a new short code.
 *
 * <p>Two real approaches:
 * <ul>
 *   <li><b>Counter</b> — atomic incrementing long. Guaranteed unique, predictable codes
 *       (each new code is one more than the last), but enumerable from outside.</li>
 *   <li><b>Random + retry</b> — generate random long, retry if collision. Unpredictable
 *       codes, but probabilistically O(1) at low load factor.</li>
 * </ul>
 *
 * <p>The {@code isAvailable} predicate lets impls test for collisions against the
 * caller's existing-code map without coupling the Strategy to the storage layer.
 */
public interface IdGenerationStrategy {

    /**
     * Generate a unique id whose base-62 encoding is NOT already taken.
     * @param isAvailable callback: "is this base-62 short code free?"
     */
    long nextId(Predicate<String> isAvailable);
}
