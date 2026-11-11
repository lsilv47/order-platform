package com.orderplatform.payment.controller;

import com.orderplatform.payment.dto.response.PaymentResponse;
import com.orderplatform.payment.exception.ResourceNotFoundException;
import com.orderplatform.payment.repository.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrderId(@PathVariable UUID orderId) {
        var payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order " + orderId));

        return ResponseEntity.ok(new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getStripePaymentIntentId(),
                payment.getCreatedAt()
        ));
    }
}
