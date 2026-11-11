package com.orderplatform.order.domain;

import com.orderplatform.order.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationsShouldRunClean() throws Exception {
        try (var conn = dataSource.getConnection();
             var rs = conn.getMetaData().getTables(null, null, "products", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
