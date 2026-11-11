package com.orderplatform.order.service;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderItem;
import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.domain.Product;
import com.orderplatform.order.dto.request.CreateOrderRequest;
import com.orderplatform.order.dto.request.OrderItemRequest;
import com.orderplatform.order.dto.response.OrderResponse;
import com.orderplatform.order.exception.InsufficientStockException;
import com.orderplatform.order.exception.InvalidOrderStateException;
import com.orderplatform.order.exception.ResourceNotFoundException;
import com.orderplatform.order.messaging.event.OrderCreatedApplicationEvent;
import com.orderplatform.order.repository.OrderRepository;
import com.orderplatform.order.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private OrderService orderService;

    // ---- helpers ----

    private Product buildProduct(UUID id, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setPrice(new BigDecimal("10.00"));
        p.setStockQuantity(stock);
        p.setReservedQuantity(0);
        p.setActive(true);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private OrderItem buildItem(UUID productId, int qty, BigDecimal price) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setSubtotal(price.multiply(BigDecimal.valueOf(qty)));
        return item;
    }

    private Order buildOrder(OrderStatus status, OrderItem... items) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCorrelationId(UUID.randomUUID().toString());
        order.setCustomerEmail("test@test.com");
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        for (OrderItem item : items) {
            item.setOrder(order);
            order.getItems().add(item);
        }
        return order;
    }

    // ---- create tests ----

    @Test
    void create_decrementStockAndReturnsOrderResponse() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, 10);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest req = new CreateOrderRequest("customer@test.com",
                List.of(new OrderItemRequest(productId, 3)));

        OrderResponse response = orderService.create(req, "corr-id");

        assertThat(product.getStockQuantity()).isEqualTo(7);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void create_sortsByProductIdBeforeLocking() {
        UUID idA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        when(productRepository.findByIdForUpdate(idA)).thenReturn(Optional.of(buildProduct(idA, 10)));
        when(productRepository.findByIdForUpdate(idB)).thenReturn(Optional.of(buildProduct(idB, 10)));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Request is [B, A] — service must lock A before B
        CreateOrderRequest req = new CreateOrderRequest("customer@test.com",
                List.of(new OrderItemRequest(idB, 1), new OrderItemRequest(idA, 1)));

        orderService.create(req, "corr-id");

        InOrder inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).findByIdForUpdate(idA);
        inOrder.verify(productRepository).findByIdForUpdate(idB);
    }

    @Test
    void create_throwsInsufficientStockException() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, 5);
        product.setReservedQuantity(3); // available = 2

        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        CreateOrderRequest req = new CreateOrderRequest("customer@test.com",
                List.of(new OrderItemRequest(productId, 3)));

        assertThatThrownBy(() -> orderService.create(req, "corr-id"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void create_publishesOrderCreatedApplicationEvent() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(buildProduct(productId, 10)));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest req = new CreateOrderRequest("customer@test.com",
                List.of(new OrderItemRequest(productId, 1)));

        orderService.create(req, "corr-id");

        verify(applicationEventPublisher).publishEvent(any(OrderCreatedApplicationEvent.class));
    }

    // ---- cancel tests ----

    @Test
    void cancel_setCancelledAndRestoresStock() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, 5);
        OrderItem item = buildItem(productId, 3, new BigDecimal("10.00"));
        Order order = buildOrder(OrderStatus.PENDING, item);

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancel(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(8); // 5 + 3
    }

    @Test
    void cancel_throwsInvalidOrderStateException_whenNotPending() {
        Order order = buildOrder(OrderStatus.PAID);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(order.getId()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---- transitionTo tests ----

    @Test
    void transitionTo_paidFromPending_setsStatusPaid() {
        Order order = buildOrder(OrderStatus.PENDING);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.transitionTo(order.getId(), OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transitionTo_paymentFailedRestoresStock() {
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, 5);
        OrderItem item = buildItem(productId, 2, new BigDecimal("10.00"));
        Order order = buildOrder(OrderStatus.PENDING, item);

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.transitionTo(order.getId(), OrderStatus.PAYMENT_FAILED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(product.getStockQuantity()).isEqualTo(7); // 5 + 2
    }

    @Test
    void transitionTo_idempotent_returnsSilentlyIfAlreadyInTargetStatus() {
        Order order = buildOrder(OrderStatus.PAID);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));

        orderService.transitionTo(order.getId(), OrderStatus.PAID);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void transitionTo_throwsInvalidOrderStateException_forInvalidTransition() {
        Order order = buildOrder(OrderStatus.PAID);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionTo(order.getId(), OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---- findById tests ----

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
