# Order Platform

Plataforma de pedidos e pagamentos orientada a eventos.

**Stack:** Java 21 · Spring Boot 3 · RabbitMQ · PostgreSQL · Stripe · Angular

## Subir a infraestrutura local

```bash
cd infra
docker compose up -d
```

| Serviço       | URL                          |
|---------------|------------------------------|
| RabbitMQ UI   | http://localhost:15672       |
| MailHog       | http://localhost:8025        |
| order-service | http://localhost:8081/swagger-ui.html |
| payment-service | http://localhost:8082/swagger-ui.html |

## Estrutura

```
services/
  order-service/        # Pedidos, estoque, JWT
  payment-service/      # Stripe, webhooks, idempotência
  notification-service/ # E-mail via RabbitMQ
frontend/               # Angular — painel admin
infra/                  # docker-compose.yml
docs/                   # Arquitetura e cenários de falha
```
