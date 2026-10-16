package com.gkcontas.multitenancy.measure;

/** How a variant is meant to be read in the report. */
public enum VariantKind {

    /** The plan the obvious code produces, and it is wrong. */
    PROBLEM,

    /** A way of getting the same answer that does not have the problem. */
    FIX,

    /** Looks like a fix, and is not - sometimes worse than doing nothing. */
    TRAP,

    /** Neither right nor wrong: the price the strategy charges. */
    COST
}
