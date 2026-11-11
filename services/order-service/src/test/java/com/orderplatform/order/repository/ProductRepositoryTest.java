package com.orderplatform.order.repository;

import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ProductRepositoryTest extends AbstractIntegrationTest {
    @Autowired ProductRepository repo;

    @Test
    void saveAndFindProduct() {
        var p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("Test");
        p.setPrice(new BigDecimal("99.90"));
        p.setStockQuantity(10);
        p.setReservedQuantity(0); // NOT NULL column not in brief snippet
        p.setActive(true);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        repo.save(p);
        assertThat(repo.findById(p.getId())).isPresent();
    }
}
