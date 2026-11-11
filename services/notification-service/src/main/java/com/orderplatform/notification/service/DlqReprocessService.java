package com.orderplatform.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DlqReprocessService {

    private static final Logger log = LoggerFactory.getLogger(DlqReprocessService.class);
    private static final String DLQ = "notification.payment-result.dlq";
    private static final String EXCHANGE = "payment.events";
    private static final int MAX_MESSAGES = 100;

    private final RabbitTemplate rabbitTemplate;

    public DlqReprocessService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public int reprocess() {
        int count = 0;
        for (int i = 0; i < MAX_MESSAGES; i++) {
            Message message = rabbitTemplate.receive(DLQ, 1000L);
            if (message == null) break;

            String routingKey = extractOriginalRoutingKey(message);
            rabbitTemplate.send(EXCHANGE, routingKey, message);
            count++;
            log.info("Reprocessed DLQ message with routing key {}", routingKey);
        }
        log.info("Reprocessed {} messages from DLQ", count);
        return count;
    }

    @SuppressWarnings("unchecked")
    private String extractOriginalRoutingKey(Message message) {
        try {
            List<Map<String, Object>> xDeath =
                    (List<Map<String, Object>>) message.getMessageProperties().getHeaders().get("x-death");
            if (xDeath != null && !xDeath.isEmpty()) {
                Object rk = xDeath.get(0).get("routing-keys");
                if (rk instanceof List<?> keys && !keys.isEmpty()) {
                    return keys.get(0).toString();
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract original routing key from x-death header", e);
        }
        return "payment.approved";
    }
}
