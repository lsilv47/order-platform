package com.orderplatform.payment.domain;

import com.orderplatform.payment.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationCreatesPaymentsTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "payments", null)) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void migrationCreatesProcessedWebhookEventsTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "processed_webhook_events", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
