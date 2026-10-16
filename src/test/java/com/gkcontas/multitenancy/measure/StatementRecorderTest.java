package com.gkcontas.multitenancy.measure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatementRecorderTest {

    @Test
    void recordsNothingUntilRecordingStarts() {
        StatementRecorder recorder = new StatementRecorder();
        recorder.inspect("select 1");
        assertThat(StatementRecorder.stop()).isEmpty();
    }

    @Test
    void recordsInOrderBetweenStartAndStop() {
        StatementRecorder recorder = new StatementRecorder();
        StatementRecorder.start();
        recorder.inspect("select 1");
        recorder.inspect("select 2");
        assertThat(StatementRecorder.stop()).containsExactly("select 1", "select 2");
    }

    @Test
    void handsBackTheSqlUnchanged() {
        assertThat(new StatementRecorder().inspect("select 1")).isEqualTo("select 1");
    }

    @Test
    void stoppingTwiceIsEmptyTheSecondTime() {
        StatementRecorder recorder = new StatementRecorder();
        StatementRecorder.start();
        recorder.inspect("select 1");
        StatementRecorder.stop();
        assertThat(StatementRecorder.stop()).isEmpty();
    }
}
