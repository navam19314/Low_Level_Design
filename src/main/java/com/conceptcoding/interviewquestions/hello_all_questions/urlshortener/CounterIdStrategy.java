package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Monotonic counter. Each call returns the next integer; never collides by
 * construction (so the {@code isAvailable} check is just a sanity check —
 * needed only if other strategies share the same id-space).
 *
 * <p>Atomic so multiple threads can call nextId concurrently without locks.
 */
public class CounterIdStrategy implements IdGenerationStrategy {

    private final AtomicLong counter;

    public CounterIdStrategy()        { this(1_000_000L); }   // start at 1M for nice ~4-char codes
    public CounterIdStrategy(long startAt) { this.counter = new AtomicLong(startAt); }

    @Override
    public long nextId(Predicate<String> isAvailable) {
        // Counter is collision-free by construction; loop only handles the (impossible
        // in practice) case where the predicate rejects a freshly-minted id.
        while (true) {
            long candidate = counter.getAndIncrement();
            String code = Base62Encoder.encode(candidate);
            if (isAvailable.test(code)) return candidate;
        }
    }
}
