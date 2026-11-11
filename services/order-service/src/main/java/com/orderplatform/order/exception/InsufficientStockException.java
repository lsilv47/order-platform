package com.orderplatform.order.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId, int available) {
        super("Insufficient stock for product " + productId + ": available=" + available);
    }
}
