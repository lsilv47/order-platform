package com.orderplatform.order.controller;

import com.orderplatform.order.domain.OrderStatus;
import com.orderplatform.order.dto.request.CreateOrderRequest;
import com.orderplatform.order.dto.response.OrderResponse;
import com.orderplatform.order.dto.response.OrderSummaryResponse;
import com.orderplatform.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.status(201).body(orderService.create(req, correlationId));
    }

    @GetMapping
    public ResponseEntity<Page<OrderSummaryResponse>> findAll(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerEmail,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(status, customerEmail, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
