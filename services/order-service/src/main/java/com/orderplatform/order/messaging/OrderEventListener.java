package com.orderplatform.order.messaging;

import com.orderplatform.order.messaging.event.OrderCreatedApplicationEvent;
import com.orderplatform.order.messaging.publisher.OrderEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class OrderEventListener {

    private final OrderEventPublisher orderEventPublisher;

    public OrderEventListener(OrderEventPublisher orderEventPublisher) {
        this.orderEventPublisher = orderEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedApplicationEvent event) {
        orderEventPublisher.publishOrderCreated(event.order(), event.correlationId());
    }
}
