package com.gkcontas.multitenancy.measure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProbeOptionsTest {

    @Test
    void fallsBackToTheDefaultsWhenTheRequestOmitsThem() {
        assertThat(ProbeOptions.of(null, null)).isEqualTo(ProbeOptions.DEFAULT);
    }

    @Test
    void neverRunsFewerThanOneTimedRepeat() {
        assertThat(ProbeOptions.of(2, 0).repeats()).isEqualTo(1);
    }

    @Test
    void allowsSkippingTheWarmups() {
        assertThat(ProbeOptions.of(0, 3)).isEqualTo(new ProbeOptions(0, 3));
    }

    @Test
    void clampsNegativeWarmups() {
        assertThat(ProbeOptions.of(-5, 3).warmups()).isZero();
    }
}
