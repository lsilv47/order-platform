package com.orderplatform.notification.service;

import com.orderplatform.notification.messaging.event.PaymentApprovedEvent;
import com.orderplatform.notification.messaging.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                     @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendPaymentApprovedEmail(PaymentApprovedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(from); // no customerEmail in event — send to default
        message.setSubject("Pedido #" + event.orderId() + " confirmado!");
        message.setText("Seu pagamento de R$ " + event.amount() + " para o pedido "
                + event.orderId() + " foi aprovado com sucesso.");

        log.info("Sending payment approved email for order {}", event.orderId());
        mailSender.send(message);
    }

    public void sendPaymentFailedEmail(PaymentFailedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(from); // no customerEmail in event — send to default
        message.setSubject("Problema no pagamento do pedido #" + event.orderId());
        message.setText("O pagamento de R$ " + event.amount() + " para o pedido "
                + event.orderId() + " falhou. Motivo: " + event.reason());

        log.info("Sending payment failed email for order {}", event.orderId());
        mailSender.send(message);
    }
}
