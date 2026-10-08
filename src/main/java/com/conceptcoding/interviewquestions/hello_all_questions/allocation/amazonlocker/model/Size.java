package com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model;

// Used for both parcels and compartments. A parcel fits any compartment at least its size.
public enum Size {
    SMALL(1), MEDIUM(2), LARGE(3);

    private final int rank;
    Size(int rank) { this.rank = rank; }
    public int getRank() { return rank; }

    public boolean canHold(Size parcel) { return rank >= parcel.rank; }
}
