package com.example.expensetracker;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExpenseTrackerApplicationTests {

    private final Flyway flyway;

    @Autowired
    ExpenseTrackerApplicationTests(Flyway flyway) {
        this.flyway = flyway;
    }

    @Test
    void startsWithTheInitialMigrationApplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
    }
}
