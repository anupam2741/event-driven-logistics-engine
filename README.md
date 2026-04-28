# Logistics System

A production-grade, event-driven logistics microservices platform that handles real-time order placement, rider dispatch, and live GPS tracking. Built with Java 21, Spring Boot 3.2.3, Kafka, gRPC, and Redis.

---

## Architecture Overview

```
┌─────────────────┐         gRPC (9090)        ┌──────────────────────┐
│   Order Service │ ◄─────────────────────────► │  Tracking Service    │
│   (port 8081)   │                             │  (port 8080/8082)    │
│                 │ ──── Kafka order-topic ───► │                      │
│                 │ ◄─── Kafka order-status ─── │                      │
└─────────────────┘                             └──────────┬───────────┘
                                                           │ Redis Pub/Sub
                                                           ▼
                                                ┌──────────────────────┐
                                                │   Rider Simulator    │
                                                │   (Python 3.11)      │
                                                │   GPS pings → HTTP   │
                                                └──────────────────────┘
```

### Services

| Service | Port | Responsibility |
|---|---|---|
| **order-service** | 8081 | Order lifecycle, customer API, live tracking |
| **tracking-service** | 8080 (container) / 8082 (host) | Rider dispatch, geospatial indexing, gRPC server |
| **rider-simulator** | — | Async Python script simulating rider GPS movement |

### Infrastructure (external, self-managed)

| Component | Purpose |
|---|---|
| **PostgreSQL** | Persistent storage for orders and riders |
| **Apache Kafka** | Async event streaming between services |
| **Redis** | Geospatial rider pool, soft-locks, pub/sub dispatch channel |

---

## Order Flow

```
1. POST /api/v1/order
   └─ gRPC CheckAvailability → tracking-service soft-locks nearest rider (30s TTL)
   └─ Order saved to DB (PENDING)
   └─ OrderEvent published to Kafka "order-topic"

2. Kafka consumer (tracking-service)
   └─ Atomic Lua script: moves rider available_riders → active_shipments (Redis geo)
   └─ Broadcasts mission to RiderSimulation.py via Redis pub/sub
   └─ Publishes RIDER_ASSIGNED to "order-status-updates"

3. Rider Simulator
   └─ Moves in 0.003° steps (~333m), pings HTTP every 2s
   └─ Sends PICKED_UP → DELIVERED status transitions

4. LocationIngestionController (tracking-service)
   └─ Updates Redis geo-index
   └─ On DELIVERED: releases rider back to available pool
   └─ Publishes status updates to Kafka

5. OrderUpdateConsumer (order-service)
   └─ Updates order status in DB
```

---

## API Reference

### Order Service (`:8081`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/order` | Place a new order |
| `GET` | `/api/v1/order/{orderId}` | Get order details |
| `PATCH` | `/api/v1/order/{orderId}/cancel` | Cancel order and release rider |
| `GET` | `/api/v1/order/{orderId}/tracking` | Get live rider coordinates |
| `POST` | `/api/v1/order/create-demo-order` | Place order with random Bangalore coordinates |

**Request body for `POST /api/v1/order`:**
```json
{
  "customerId": "customer-123",
  "pickupAddress": { "lat": 12.9716, "lng": 77.5946 },
  "deliveryAddress": { "lat": 12.9800, "lng": 77.6100 },
  "totalAmount": 250.00,
  "priority": "MEDIUM"
}
```

### Tracking Service (`:8082`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/tracking/ping` | Ingest GPS ping from rider |
| `POST` | `/api/riders/location` | Manually update rider location |
| `POST` | `/api/riders/seedRiders` | Reset and seed 10 test riders |

### Authentication

All endpoints require `X-API-Key: <API_KEY>` header. gRPC calls use the same key as `x-api-key` metadata.

### Swagger UI

- Order Service: `http://<host>:8081/swagger-ui/`
- Tracking Service: `http://<host>:8082/swagger-ui/`

---

## gRPC Service

Defined in `common-lib/src/main/proto/rider_availability.proto`.

```proto
service RiderDiscoveryService {
  rpc CheckAvailability(AvailabilityRequest) returns (AvailabilityResponse);
  rpc GetLiveLocation(LocationRequest)       returns (LocationResponse);
  rpc ReleaseRider(ReleaseRiderRequest)      returns (ReleaseRiderResponse);
}
```

`CheckAvailability` soft-locks the nearest rider in Redis for 30 seconds to prevent double-assignment races.

---

## Resilience

| Circuit Breaker | Failure Threshold | Wait | Fallback |
|---|---|---|---|
| `canPlaceOrder` | 50% in 10 calls | 30s | Return `is_available=false` |
| `fetchLiveLocation` | 60% in 10 calls | 20s | Return `(0.0, 0.0), is_active=false` |
| `cancelOrder` | 70% in 10 calls | 20s | Persist to `pending_rider_releases` table, retry every 30s |

The `PendingRiderReleasePoller` runs every 30 seconds to retry any rider releases that failed while the circuit was open. It deletes each row only after a successful gRPC call, surviving pod restarts.

Tracking-service Kafka consumer uses a Dead Letter Topic (`order-topic.DLT`) for orders that fail assignment after retries.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.3 |
| Messaging | Apache Kafka |
| RPC | gRPC 1.62.2 + Protobuf 3.25.3 |
| Cache / Geo | Redis (Lettuce client, connection pool) |
| Database | PostgreSQL + Spring Data JPA + HikariCP |
| Resilience | Resilience4j 2.2.0 |
| Security | Spring Security 6 (API key filter + gRPC interceptor) |
| API Docs | SpringDoc OpenAPI 2.3.0 |
| Observability | Spring Actuator (liveness + readiness probes) |
| Simulation | Python 3.11 + httpx + redis-py |
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions (self-hosted runner, Oracle A1.Flex ARM64) |

---

## Project Structure

```
logistic-system/
├── common-lib/                  # Shared DTOs, Kafka topic constants, protobuf definitions
├── order-service/               # Order management microservice
│   ├── src/main/java/com/project/
│   │   ├── restController/      # OrderController
│   │   ├── service/             # OrderServiceImpl, OrderTrackingClient, OrderSafetyNetService
│   │   ├── entity/              # OrderEntity, OutboxEvent, PendingRiderRelease
│   │   ├── kafka/               # Producers and consumers
│   │   ├── outbox/              # OutboxPoller, PendingRiderReleasePoller
│   │   └── security/            # ApiKeyAuthFilter, GrpcApiKeyClientInterceptor
│   └── Dockerfile
├── tracking-service/            # Rider tracking and dispatch microservice
│   ├── src/main/java/com/project/
│   │   ├── controller/          # LocationIngestionController, RiderController
│   │   ├── service/             # RiderDiscoveryGrpcServer, RiderAssignmentService
│   │   ├── entity/              # RiderEntity
│   │   ├── kafka/               # OrderConsumerService, OrderStatusProducer
│   │   └── security/            # ApiKeyAuthFilter, GrpcApiKeyServerInterceptor
│   └── Dockerfile
├── RiderSimulation.py           # Python async rider movement simulator
├── Dockerfile.simulator
├── docker-compose.yml
├── pom.xml                      # Parent POM (Java 21, Spring Boot 3.2.3)
└── .github/workflows/deploy.yml
```

---

## Local Setup

### Prerequisites

- Java 21
- Maven 3.8+
- Docker + Docker Compose
- A running PostgreSQL, Kafka, and Redis instance

### 1. Configure environment

Create a `.env` file at the project root:

```env
DB_URL=jdbc:postgresql://<host>:5432/OrderDetails
DB_USERNAME=<user>
DB_PASSWORD=<password>
KAFKA_BOOTSTRAP_SERVERS=<host>:9092
REDIS_HOST=<host>
REDIS_PASSWORD=<password>
API_KEY=<your-api-key>
TRACKING_SERVICE_HOST=tracking-service
TRACKING_URL=http://tracking-service:8080/api/tracking/ping
SERVER_HOST=http://localhost
```

### 2. Build

```bash
mvn clean install -DskipTests
```

### 3. Run with Docker Compose

```bash
docker compose up -d --build
```

Services start in order: tracking-service (health check passes) → order-service → rider-simulator.

### 4. Seed riders

```bash
curl -X POST http://localhost:8082/api/riders/seedRiders \
  -H "X-API-Key: <API_KEY>"
```

### 5. Place a demo order

```bash
curl -X POST "http://localhost:8081/api/v1/order/create-demo-order?priority=MEDIUM" \
  -H "X-API-Key: <API_KEY>"
```

---

## CI/CD

Pushes to `master` trigger the GitHub Actions workflow:

1. Write `.env` from GitHub repository secrets
2. `mvn clean install -DskipTests` (builds common-lib first, then services)
3. `docker compose down --remove-orphans`
4. `docker compose up -d --build`
5. `docker image prune -f`

**Required GitHub Secrets:**

| Secret | Description |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB username |
| `DB_PASSWORD` | DB password |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap address |
| `REDIS_HOST` | Redis host |
| `REDIS_PASSWORD` | Redis password |
| `API_KEY` | API key for service authentication |
| `SERVER_HOST` | Public server IP/hostname (e.g. `http://1.2.3.4`) |

---

## Health Checks

| Service | Endpoint |
|---|---|
| Order Service liveness | `GET :8081/actuator/health/liveness` |
| Order Service readiness | `GET :8081/actuator/health/readiness` |
| Tracking Service liveness | `GET :8082/actuator/health/liveness` |
| Tracking Service readiness | `GET :8082/actuator/health/readiness` |

No authentication required on health endpoints.

---

## Debugging

```bash
# View logs for a service
docker logs logistic-system-order-service-1 -f
docker logs logistic-system-tracking-service-1 -f
docker logs logistic-system-rider-simulator-1 -f

# Check container status
docker compose ps

# Exec into a container
docker compose exec order-service sh
```
