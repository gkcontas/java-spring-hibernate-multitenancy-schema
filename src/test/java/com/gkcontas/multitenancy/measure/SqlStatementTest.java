package com.gkcontas.multitenancy.measure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlStatementTest {

    @Test
    void collapsesRepeatedTextAndKeepsFirstSeenOrder() {
        List<SqlStatement> collapsed = SqlStatement.collapse(List.of(
                "select 1", "select 2", "select 1", "select 1"));

        assertThat(collapsed).containsExactly(
                new SqlStatement("select 1", 3),
                new SqlStatement("select 2", 1));
    }

    @Test
    void handlesNoStatementsAtAll() {
        assertThat(SqlStatement.collapse(List.of())).isEmpty();
    }
}
