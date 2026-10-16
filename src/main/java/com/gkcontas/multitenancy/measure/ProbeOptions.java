package com.gkcontas.multitenancy.measure;

/**
 * Warm-ups are discarded: the first run of anything pays JIT, the Hibernate
 * query plan cache and the PostgreSQL plan cache, and none of that is what a
 * scenario is comparing. The reported time is the median of the timed runs.
 */
public record ProbeOptions(int warmups, int repeats) {

    public static final ProbeOptions DEFAULT = new ProbeOptions(3, 9);

    public static ProbeOptions of(Integer warmups, Integer repeats) {
        return new ProbeOptions(
                warmups == null ? DEFAULT.warmups() : Math.max(0, warmups),
                repeats == null ? DEFAULT.repeats() : Math.max(1, repeats));
    }

    public static final ProbeOptions ONCE = new ProbeOptions(0, 1);
}
