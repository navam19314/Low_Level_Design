package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Everyone pays the same. Leftover rupees go to the last person: ₹100 / 3 → 33, 33, 34.
public class EqualSplit implements SplitStrategy {

    private final List<String> people;

    public EqualSplit(List<String> people) {
        if (people.isEmpty()) throw new IllegalArgumentException("Nobody to split with");
        if (new HashSet<>(people).size() != people.size()) throw new IllegalArgumentException("Same person twice: " + people);
        this.people = new ArrayList<>(people);
    }

    @Override
    public Map<String, Long> shares(long amount) {
        Map<String, Long> shares = new LinkedHashMap<>();
        long each = amount / people.size();
        for (String person : people) shares.put(person, each);
        String last = people.get(people.size() - 1);
        shares.put(last, each + amount % people.size());        // the leftover rupees
        return shares;
    }
}
