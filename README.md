# Online Learning Platform (Backend Microservices)

This backend is built with **Java 17 + Spring Boot + Spring Cloud** and follows a microservices architecture with:

- Discovery Server (Eureka)
- Config Server
- API Gateway
- Auth Service (Google OAuth2)
- User Service
- Course Service
- Enrollment Service
- **Payment Service (main focus)**
- Notification Service
- Redis (for payment idempotency/locking/cache)
- Separate MySQL database per domain service

---

## 1) Prerequisites

- Java 17
- Maven 3.9+
- Docker + Docker Compose

## 1.1) Docker On Mac And Windows

The container stack uses Linux images, so it runs the same way on macOS and on Windows Docker Desktop as long as Docker is set to Linux containers.

- macOS on Apple Silicon will pull arm64-compatible images.
- Windows on x86_64 will pull amd64-compatible images.
- If you publish images to a registry and want one tag to serve both platforms, build and push a multi-arch manifest with `docker buildx`.

Example multi-arch publish command:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t your-registry/holyaf/discovery-service:local \
  --push \
  --build-arg SERVICE_NAME=discovery-service \
  -f Dockerfile .
```

The same pattern can be repeated for each service image.

---

## 2) Start Infrastructure (Redis + all DBs)

From `backend/`:

```bash
docker compose up -d
```

Databases:

- `auth_db` on `localhost:3307`
- `user_db` on `localhost:3308`
- `course_db` on `localhost:3309`
- `enrollment_db` on `localhost:3310`
- `payment_db` on `localhost:3311`
- `notification_db` on `localhost:3312`
- Redis on `localhost:6379`

---

## 3) Configure Google OAuth

Set env variables before running services:

### PowerShell
```powershell
$env:GOOGLE_CLIENT_ID="<your-google-client-id>"
$env:GOOGLE_CLIENT_SECRET="<your-google-client-secret>"
```

Use these Google console redirect values (as per your screenshot):

- Authorized JavaScript origins:
  - `http://localhost:3000`
  - `http://localhost:8082`
  - `http://localhost:8080`
- Authorized redirect URIs:
  - `http://localhost:8082/login/oauth2/code/google`
  - `http://localhost:8080/login/oauth2/code/google`

---

## 4) Run Services (order)

Run each command in separate terminal tabs:

```bash
mvn -pl discovery-service spring-boot:run
mvn -pl config-service spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl auth-service spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl course-service spring-boot:run
mvn -pl enrollment-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl notification-service spring-boot:run
```

---

## 5) Verify Build

```bash
mvn -DskipTests compile
```

---

## 6) Core Endpoints

### Health/Infra
- Eureka: `http://localhost:8761`
- Config server: `http://localhost:8888`
- Gateway: `http://localhost:8080`

### Auth (Google OAuth)
- Start login: `http://localhost:8082/oauth2/authorization/google`
- Success callback: `http://localhost:8082/auth/login/success`

### Payment Service (Main Focus)

#### Create payment (idempotent)
```bash
curl -X POST http://localhost:8087/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-order-1001" \
  -d '{
    "userId": 1,
    "courseId": 101,
    "enrollmentId": 5001,
    "amount": 999.00,
    "currency": "INR"
  }'
```

#### Get payment by id
```bash
curl http://localhost:8087/payments/1
```

#### Simulated webhook update
```bash
curl -X POST http://localhost:8087/payments/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "gatewayTransactionId": "GTX-REPLACE",
    "status": "FAILED",
    "failureReason": "Insufficient funds"
  }'
```

---

## 7) Payment Service Design Highlights

- **Idempotency support** via `Idempotency-Key` header + DB unique key.
- **Redis lock** (`payment:lock:<idempotencyKey>`) prevents concurrent duplicate processing.
- **Payment lifecycle**: `INITIATED`, `PROCESSING`, `SUCCESS`, `FAILED`, `REFUNDED`.
- **Webhook-ready endpoint** for gateway callback updates.
- **Enrollment integration hook**: payment success attempts to mark enrollment status as `ACTIVE`.
- **Dedicated DB** (`payment_db`) + Redis cache entry for fast status lookup.
