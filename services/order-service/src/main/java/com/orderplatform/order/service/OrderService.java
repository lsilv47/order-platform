package com.orderplatform.order.service;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderItem;
import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.domain.Product;
import com.orderplatform.order.dto.request.CreateOrderRequest;
import com.orderplatform.order.dto.request.OrderItemRequest;
import com.orderplatform.order.dto.response.OrderItemResponse;
import com.orderplatform.order.dto.response.OrderResponse;
import com.orderplatform.order.dto.response.OrderSummaryResponse;
import com.orderplatform.order.exception.InsufficientStockException;
import com.orderplatform.order.exception.InvalidOrderStateException;
import com.orderplatform.order.exception.ResourceNotFoundException;
import com.orderplatform.order.repository.OrderRepository;
import com.orderplatform.order.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest req, String correlationId) {
        Instant now = Instant.now();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        order.setCustomerEmail(req.customerEmail());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : req.items()) {
            Product product = productRepository.findByIdForUpdate(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));

            int available = product.getStockQuantity() - product.getReservedQuantity();
            if (available < itemReq.quantity()) {
                throw new InsufficientStockException(product.getId().toString(), available);
            }

            product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());
            product.setUpdatedAt(now);
            productRepository.save(product);

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(subtotal);

            OrderItem item = new OrderItem();
            item.setId(UUID.randomUUID());
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(product.getPrice());
            item.setSubtotal(subtotal);
            order.getItems().add(item);
        }

        order.setTotalAmount(total);
        return toResponse(orderRepository.save(order));
    }

    public Page<OrderSummaryResponse> findAll(OrderStatus status, String customerEmail, Pageable pageable) {
        Page<Order> page;
        if (status != null && customerEmail != null) {
            page = orderRepository.findAllByStatusAndCustomerEmailContainingIgnoreCase(status, customerEmail, pageable);
        } else if (status != null) {
            page = orderRepository.findAllByStatus(status, pageable);
        } else if (customerEmail != null) {
            page = orderRepository.findAllByCustomerEmailContainingIgnoreCase(customerEmail, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }
        return page.map(this::toSummary);
    }

    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(order.getStatus().name(), "CANCELLED");
        }
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public void transitionTo(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        OrderStatus current = order.getStatus();
        boolean valid = current == OrderStatus.PENDING &&
                (newStatus == OrderStatus.PAID || newStatus == OrderStatus.PAYMENT_FAILED);
        if (!valid) {
            throw new InvalidOrderStateException(current.name(), newStatus.name());
        }
        if (newStatus == OrderStatus.PAYMENT_FAILED) {
            restoreStock(order);
        }
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    private void restoreStock(Order order) {
        Instant now = Instant.now();
        for (OrderItem item : order.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                product.setUpdatedAt(now);
                productRepository.save(product);
            });
        }
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderResponse(o.getId(), o.getStatus().name(), o.getTotalAmount(),
                o.getCustomerEmail(), items, o.getCreatedAt(), o.getUpdatedAt());
    }

    private OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getStatus().name(), o.getTotalAmount(),
                o.getCustomerEmail(), o.getCreatedAt());
    }
}
