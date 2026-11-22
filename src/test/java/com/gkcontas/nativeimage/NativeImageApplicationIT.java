package com.gkcontas.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NativeImageApplicationIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatedTheSchema() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isZero();
    }
}
