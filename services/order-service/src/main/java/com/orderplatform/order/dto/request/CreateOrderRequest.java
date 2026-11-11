package com.orderplatform.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @Email @NotBlank String customerEmail,
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
