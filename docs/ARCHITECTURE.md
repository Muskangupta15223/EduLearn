# Architecture & Setup Diagrams

## High-Level Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         USER BROWSER                                      │
│                     http://localhost:5173                                 │
└─────────────────────────────────┬─────────────────────────────────────────┘
                                  │
                                  │ HTTP Requests
                                  ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    VITE FRONTEND (React)                                  │
│                    Running in Docker Container                            │
│  - Vite dev server on :5173                                              │
│  - Proxies all API calls to localhost:8080 (API Gateway)                │
│  - No CORS issues in development                                         │
└─────────────────────────────────┬─────────────────────────────────────────┘
                                  │
                                  │ Proxied to :8080
                                  ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                  API GATEWAY (Spring Boot)                               │
│                      :8080 (main entry)                                  │
│  - Routes to appropriate microservice                                    │
│  - Adds CORS headers (allows localhost:5173)                             │
│  - Authenticates requests (JWT validation)                               │
│  - Rate limiting, request logging                                        │
└──────┬──────┬──────┬────────┬──────────┬──────────┬──────────┬──────────┘
       │      │      │        │          │          │          │
       │      │      │        │          │          │          │
   :8001  :8002  :8003    :8004      :8005      :8006      :8761
       │      │      │        │          │          │          │
       ▼      ▼      ▼        ▼          ▼          ▼          ▼
    ┌──────────────────────────────────────────────────────────┐
    │          MICROSERVICES (Spring Boot, each :80xx)         │
    ├──────────────────────────────────────────────────────────┤
    │ Auth Svc    │ User Svc    │ Course Svc                   │
    │ :8001       │ :8002       │ :8003                        │
    │ - Register  │ - Profile   │ - CRUD courses              │
    │ - Login     │ - Update    │ - Status mgmt               │
    │ - Tokens    │ - Profile   │ - Filtering                 │
    ├──────────────────────────────────────────────────────────┤
    │ Enrollment  │ Payment Svc │ Notif Svc  │ Discovery Svc  │
    │ Svc :8004   │ :8005       │ :8006      │ :8761 (Eureka) │
    │ - Enroll    │ - Razorpay  │ - Email    │ - Service      │
    │ - Unenroll  │ - Refund    │ - SMS      │   registry     │
    │ - Progress  │ - Verify    │ - Push     │ - Heart beat   │
    └──────┬───────────┬──────────┬────────────┬───────────────┘
           │           │          │            │
           │           ▼          ▼            │
           │        ┌──────────────────┐       │
           │        │  KAFKA            │       │
           │        │  Message Queue    │       │
           │        │  :9092            │       │
           │        │ - Async events    │       │
           │        │ - Payment events  │       │
           │        │ - Notification    │       │
           │        └──────────────────┘       │
           │                                   │
           ▼                                   ▼
    ┌────────────────────┐            ┌──────────────────┐
    │  MYSQL (6 dbs)     │            │  POSTGRESQL      │
    │  :3307-3312        │            │  (SonarQube)     │
    │                    │            │  :5432           │
    │ - auth-db          │            │  sonar_db        │
    │ - user-db          │            └──────────────────┘
    │ - course-db        │                    ▲
    │ - enrollment-db    │                    │
    │ - payment-db       │            ┌──────────────────┐
    │ - notification-db  │            │  SONARQUBE       │
    └────────────────────┘            │  (Code Quality)  │
                                      │  :9000           │
    ┌────────────────────┐            └──────────────────┘
    │  REDIS             │
    │  Cache             │
    │  :6379             │
    │ - Sessions         │
    │ - Tokens           │
    │ - Cache            │
    └────────────────────┘
```

---

## Startup Sequence (What Happens When You Run `docker-run.ps1 up`)

```
TIME 0s
├─ Docker Compose starts all containers
│
├─ Zookeeper starts (:2181)
│
├─ Kafka starts (:9092)
│  └─ Waits for Zookeeper
│
├─ MySQL databases start (:3307-3312)
│  └─ Create schemas from Spring Boot JPA
│
├─ PostgreSQL starts (:5432)
│  └─ Initializes sonar database
│
└─ Redis starts (:6379)

TIME 10-15s
├─ Discovery Service starts (:8761)
│  └─ Eureka server, self-registers as UP
│
└─ Config Service starts (:8888)
   └─ Waits for Discovery Service
   └─ Self-registers with Eureka

TIME 25-30s
├─ API Gateway starts (:8080)
│  ├─ Connects to Discovery Service
│  ├─ Loads config from Config Service
│  └─ Waits for other services to register
│
├─ Auth Service starts (:8001)
│  ├─ Connects to auth-db
│  ├─ Connects to Redis
│  ├─ Registers with Eureka
│  └─ Ready to authenticate requests
│
├─ User Service starts (:8002)
│  ├─ Connects to user-db
│  ├─ Registers with Eureka
│  └─ Ready for profile operations
│
├─ Course Service starts (:8003)
│  ├─ Connects to course-db
│  ├─ Registers with Eureka
│  └─ Ready for course operations
│
├─ Enrollment Service starts (:8004)
│  ├─ Connects to enrollment-db
│  ├─ Connects to Kafka
│  ├─ Registers with Eureka
│  └─ Ready for enrollment operations
│
├─ Payment Service starts (:8005)
│  ├─ Connects to payment-db
│  ├─ Connects to Kafka
│  ├─ Registers with Eureka
│  └─ Ready for payment processing
│
├─ Notification Service starts (:8006)
│  ├─ Connects to notification-db
│  ├─ Connects to Kafka
│  ├─ Registers with Eureka
│  └─ Ready to send emails
│
└─ SonarQube starts (:9000)
   ├─ Waits for PostgreSQL
   └─ Initializes quality dashboard

TIME 45-90s
└─ Frontend (React + Vite) starts (:5173)
   ├─ npm install
   ├─ npm run dev
   ├─ Vite dev server ready
   └─ Proxies all requests to :8080
```

---

## Request Flow Example: User Registration

```
STEP 1: User Types Registration Data
┌──────────────────┐
│ Browser :5173    │
│ "Register" form  │
└────────┬─────────┘
         │
         │ POST /auth/public/register
         │ {email, name, password, role}
         │
         ▼
┌──────────────────────────────────────┐
│ Vite Dev Server :5173                │
│ (intercepts request via proxy config)│
└────────┬─────────────────────────────┘
         │
         │ Forwards to http://localhost:8080/auth/...
         │
         ▼
┌──────────────────────────────────────┐
│ API Gateway :8080                    │
│ 1. Check CORS (allow :5173? YES)     │
│ 2. Route to auth-service via Eureka  │
│ 3. Add headers (tracking, etc)       │
└────────┬─────────────────────────────┘
         │
         │ Forwards to Auth Service :8001 (via service name)
         │
         ▼
┌──────────────────────────────────────┐
│ Auth Service :8001                   │
│ 1. Validate input                    │
│ 2. Hash password (bcrypt)            │
│ 3. Save to auth-db                   │
│ 4. Publish event to Kafka            │
└────────┬─────────────────────────────┘
         │
         ├─→ INSERT INTO users
         │   auth-db:3307
         │
         └─→ PUBLISH "USER_REGISTERED"
             Kafka:9092
             (notification-service listens)

STEP 2: Kafka Event Processing
Notification Service :8006
├─ Receives USER_REGISTERED event
├─ Generates welcome email
└─ Sends via SMTP (muskangupta15223@gmail.com)

STEP 3: Response Back to Browser
Auth Service :8001
└─ Returns JWT token, user data

API Gateway :8080
└─ Adds CORS headers
└─ Forwards response back

Browser :5173
└─ Receives response
└─ Stores JWT in localStorage
└─ Redirects to dashboard
```

---

## Data Flow: Course Enrollment

```
1. Student Views Course
   Browser → API Gateway → Course Service → course-db → Response

2. Student Clicks "Enroll"
   Browser → API Gateway → Enrollment Service
   │
   └─ Enrollment Service
      ├─ Check if free (price = 0) or paid
      ├─ If free: create enrollment immediately
      ├─ If paid: redirect to payment
      ├─ Store in enrollment-db
      ├─ Publish "ENROLLMENT_CREATED" to Kafka
      └─ Return success

3. Kafka Event Handling
   Notification Service listens for ENROLLMENT_CREATED
   ├─ Generate confirmation email
   ├─ Send via SMTP
   └─ Confirm email sent (optional Kafka event)

4. Student Sees Course in Dashboard
   Browser → API Gateway → Enrollment Service
   └─ Get all enrollments for user (from enrollment-db)
```

---

## Database Schema Overview

```
┌─────────────────────────────────────────────────────────────┐
│ auth-db (MySQL :3307)                                       │
├─────────────────────────────────────────────────────────────┤
│ users                                                       │
│ - id (PK)                                                   │
│ - email (UNIQUE)                                            │
│ - password_hash                                             │
│ - role (STUDENT, INSTRUCTOR, ADMIN)                         │
│ - created_at                                                │
│ - updated_at                                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ user-db (MySQL :3308)                                       │
├─────────────────────────────────────────────────────────────┤
│ profiles                                                    │
│ - id (PK)                                                   │
│ - user_id (FK → auth-db.users.id)                          │
│ - full_name                                                 │
│ - bio                                                       │
│ - avatar_url                                                │
│ - updated_at                                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ course-db (MySQL :3309)                                     │
├─────────────────────────────────────────────────────────────┤
│ courses                                                     │
│ - id (PK)                                                   │
│ - instructor_id (FK → auth-db.users.id)                    │
│ - title                                                     │
│ - description                                               │
│ - price (DECIMAL)                                           │
│ - status (DRAFT, PUBLISHED, REJECTED)                       │
│ - created_at                                                │
│ - updated_at                                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ enrollment-db (MySQL :3310)                                 │
├─────────────────────────────────────────────────────────────┤
│ enrollments                                                 │
│ - id (PK)                                                   │
│ - student_id (FK → auth-db.users.id)                       │
│ - course_id (FK → course-db.courses.id)                    │
│ - status (ACTIVE, COMPLETED, CANCELLED)                     │
│ - progress (0-100)                                          │
│ - enrolled_at                                               │
│ - completed_at (nullable)                                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ payment-db (MySQL :3311)                                    │
├─────────────────────────────────────────────────────────────┤
│ payments                                                    │
│ - id (PK)                                                   │
│ - enrollment_id (FK → enrollment-db.enrollments.id)        │
│ - amount (DECIMAL)                                          │
│ - razorpay_payment_id                                       │
│ - status (PENDING, SUCCESS, FAILED, REFUNDED)               │
│ - created_at                                                │
│ - processed_at                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ notification-db (MySQL :3312)                               │
├─────────────────────────────────────────────────────────────┤
│ notifications                                               │
│ - id (PK)                                                   │
│ - user_id (FK → auth-db.users.id)                          │
│ - type (EMAIL, SMS, PUSH)                                   │
│ - message                                                   │
│ - recipient (email or phone)                                │
│ - status (PENDING, SENT, FAILED)                            │
│ - created_at                                                │
│ - sent_at (nullable)                                        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ sonar (PostgreSQL :5432)                                    │
├─────────────────────────────────────────────────────────────┤
│ (managed by SonarQube application)                          │
│ - Stores code quality metrics                               │
│ - Stores analysis results                                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Environment Variables & Configuration

```
.env (in project root)
├─ GOOGLE_CLIENT_ID          → OAuth login
├─ GOOGLE_CLIENT_SECRET      → OAuth login
├─ SPRING_DATASOURCE_USERNAME → MySQL user (all dbs)
├─ SPRING_DATASOURCE_PASSWORD → MySQL password
├─ JWT_SECRET                → JWT signing key
├─ RAZORPAY_KEY_ID           → Payment gateway
├─ RAZORPAY_KEY_SECRET       → Payment gateway
├─ MAIL_USERNAME             → Email sender (SMTP)
├─ MAIL_PASSWORD             → Email password (app password)
├─ EUREKA_USER               → Discovery service
├─ EUREKA_PASSWORD           → Discovery service
├─ KAFKA_SERVERS             → Message queue
├─ REDIS_HOST                → Cache server
└─ VITE_API_GATEWAY_URL      → Frontend API base (http://localhost:8080 in dev)
```

---

## Docker Compose File Structure

```
docker-compose.yml
├─ services:
│  ├─ Infrastructure Layer
│  │  ├─ zookeeper (confluentinc/cp-zookeeper:7.5.0)
│  │  ├─ kafka (confluentinc/cp-kafka:7.5.0)
│  │  ├─ redis (redis:7-alpine)
│  │  ├─ auth-db, user-db, course-db, ... (mysql:8.0)
│  │  ├─ sonar-db (postgres:15-alpine)
│  │  └─ sonarqube (sonarqube:lts-community)
│  │
│  ├─ Microservices Layer (all built from backend/Dockerfile)
│  │  ├─ discovery-service
│  │  ├─ config-service
│  │  ├─ api-gateway
│  │  ├─ auth-service
│  │  ├─ user-service
│  │  ├─ course-service
│  │  ├─ enrollment-service
│  │  ├─ payment-service
│  │  └─ notification-service
│  │
│  └─ Frontend Layer
│     └─ frontend (node:18-alpine with npm run dev)
│
├─ volumes:
│  └─ Named volumes for data persistence
│
└─ networks:
   └─ olp-network (connects all containers)
```

---

## Typical Development Workflow

```
1. Start Everything
   .\docker-run.ps1 up

2. Verify All Running
   docker compose ps
   http://localhost:8761 (check Eureka)

3. Make Code Changes (any service or frontend)
   Example: Update CourseController.java

4. Rebuild Service (if Java service changed)
   .\docker-run.ps1 build
   .\docker-run.ps1 up

5. Test in Browser
   http://localhost:5173

6. Check Logs if Issues
   docker compose logs <service-name>

7. When Done
   .\docker-run.ps1 down
```

**That's your complete microservices architecture!**
