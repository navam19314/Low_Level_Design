package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model;

/**
 * Immutable product record. {@code slot} is the column code visible on the
 * machine ("A1", "B3", ...) — the user selects by slot, not by name.
 */
public record Product(String slot, String name, int priceCents) {

    public Product {
        if (slot == null || slot.isBlank())  throw new IllegalArgumentException("slot required");
        if (name == null || name.isBlank())  throw new IllegalArgumentException("name required");
        if (priceCents <= 0)                  throw new IllegalArgumentException("priceCents must be > 0");
    }
}
