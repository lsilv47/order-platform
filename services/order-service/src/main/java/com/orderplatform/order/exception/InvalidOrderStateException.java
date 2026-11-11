package com.orderplatform.order.exception;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String current, String attempted) {
        super("Cannot transition order from " + current + " to " + attempted);
    }
}
