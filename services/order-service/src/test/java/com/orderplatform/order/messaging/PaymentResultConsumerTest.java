package com.orderplatform.order.messaging;

import com.orderplatform.order.AbstractIntegrationTest;
import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderItem;
import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.domain.Product;
import com.orderplatform.order.messaging.event.PaymentApprovedEvent;
import com.orderplatform.order.repository.OrderRepository;
import com.orderplatform.order.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class PaymentResultConsumerTest extends AbstractIntegrationTest {

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;

    @Test
    void paymentApproved_transitionsOrderToPaid_stockNotRestored() {
        // arrange: product with 8 units (simulating 2 were decremented on order creation)
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Test Product");
        product.setDescription("desc");
        product.setPrice(new BigDecimal("10.00"));
        product.setStockQuantity(8);
        product.setReservedQuantity(0);
        product.setActive(true);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);

        UUID orderId = UUID.randomUUID();
        String correlationId = "test-corr-" + orderId;

        Order order = new Order();
        order.setId(orderId);
        order.setCorrelationId(correlationId);
        order.setCustomerEmail("buyer@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(product.getId());
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setSubtotal(new BigDecimal("20.00"));
        order.setItems(List.of(item));
        orderRepository.save(order);

        // act: send approved event to payment.events exchange
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                UUID.randomUUID(), correlationId, orderId,
                UUID.randomUUID(), "buyer@example.com", new BigDecimal("20.00"), Instant.now());
        rabbitTemplate.convertAndSend("payment.events", "payment.approved", event, msg -> {
            msg.getMessageProperties().setCorrelationId(correlationId);
            return msg;
        });

        // assert: order transitions to PAID within 5 seconds
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order found = orderRepository.findById(orderId).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OrderStatus.PAID);
        });

        // assert: stock was NOT restored (stays at 8, not 10)
        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertThat(p.getStockQuantity()).isEqualTo(8);
    }
}
