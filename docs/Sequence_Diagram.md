# EduLearn — Sequence Diagrams

## 1. User Registration Flow

```mermaid
sequenceDiagram
    actor Student
    participant FE as React Frontend
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant KAFKA as Apache Kafka
    participant USER as User Service
    participant NOTIF as Notification Service
    participant DB as Auth DB

    Student->>FE: Fill registration form
    FE->>GW: POST /auth/public/register
    GW->>AUTH: Forward (public path, no JWT needed)
    AUTH->>DB: Check if email exists
    DB-->>AUTH: Not found
    AUTH->>DB: Save new AuthUser
    DB-->>AUTH: Saved user with ID
    AUTH->>AUTH: Generate JWT Token
    AUTH->>KAFKA: Publish to user-sync-topic
    AUTH->>KAFKA: Publish to notification-topic
    AUTH-->>GW: 200 OK {token, user}
    GW-->>FE: JWT + User Info
    FE-->>Student: Redirect to Dashboard

    Note over KAFKA,USER: Async Processing
    KAFKA-->>USER: Consume user-sync event
    USER->>USER: Create UserProfile
    KAFKA-->>NOTIF: Consume notification event
    NOTIF->>NOTIF: Save notification log
    NOTIF->>NOTIF: Send welcome email
```

## 2. User Login Flow

```mermaid
sequenceDiagram
    actor Student
    participant FE as React Frontend
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant KAFKA as Apache Kafka

    Student->>FE: Enter credentials
    FE->>GW: POST /auth/public/login
    GW->>AUTH: Forward (public path)
    AUTH->>AUTH: Validate credentials (BCrypt)
    alt Valid Credentials
        AUTH->>AUTH: Generate new JWT Token
        AUTH->>KAFKA: Publish login notification
        AUTH-->>GW: 200 OK {token, user}
        GW-->>FE: JWT Token
        FE-->>Student: Redirect to Dashboard
    else Invalid Credentials
        AUTH-->>GW: 401 Unauthorized
        GW-->>FE: Error
        FE-->>Student: Show error message
    end
```

## 3. Course Enrollment & Payment Flow

```mermaid
sequenceDiagram
    actor Student
    participant FE as React Frontend
    participant GW as API Gateway
    participant JWT as JWT Filter
    participant ENROLL as Enrollment Service
    participant PAY as Payment Service
    participant RAZOR as Razorpay
    participant REDIS as Redis

    Student->>FE: Click "Enroll"
    FE->>GW: POST /enrollments (JWT Header)
    GW->>JWT: Validate JWT
    JWT->>ENROLL: Forward with X-User headers
    ENROLL->>ENROLL: Create enrollment (PENDING_PAYMENT)
    ENROLL-->>FE: Enrollment ID

    FE->>GW: POST /payments (JWT Header)
    GW->>JWT: Validate JWT
    JWT->>PAY: Forward with X-User headers
    PAY->>REDIS: Acquire idempotency lock
    PAY->>RAZOR: Create Razorpay Order
    RAZOR-->>PAY: Order ID
    PAY->>PAY: Save transaction (PENDING)
    PAY-->>FE: {razorpayOrderId, amount}

    FE->>RAZOR: Open Razorpay checkout
    Student->>RAZOR: Complete payment
    RAZOR-->>FE: {paymentId, signature}

    FE->>GW: POST /payments/verify
    JWT->>PAY: Forward
    PAY->>RAZOR: Verify signature
    PAY->>PAY: Update status -> SUCCESS
    PAY->>ENROLL: PUT /enrollments/{id}/status?status=ACTIVE
    PAY-->>FE: Payment confirmed
    FE-->>Student: Access granted
```

## 4. Forgot Password / OTP Flow

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant EMAIL as Email Service

    User->>FE: Click "Forgot Password"
    FE->>GW: POST /auth/public/forgot-password
    GW->>AUTH: Forward
    AUTH->>AUTH: Generate 6-digit OTP
    AUTH->>AUTH: Save OTP with 10min expiry
    AUTH->>EMAIL: Send OTP email
    AUTH-->>FE: "OTP sent"

    User->>FE: Enter OTP
    FE->>GW: POST /auth/public/verify-otp
    GW->>AUTH: Forward
    AUTH->>AUTH: Validate OTP & expiry
    AUTH-->>FE: "OTP verified"

    User->>FE: Enter new password
    FE->>GW: POST /auth/public/reset-password
    GW->>AUTH: Forward
    AUTH->>AUTH: Verify OTP again
    AUTH->>AUTH: Hash new password (BCrypt)
    AUTH->>AUTH: Clear OTP, generate new session
    AUTH-->>FE: "Password reset successfully"
```

## 5. API Gateway JWT Validation Flow

```mermaid
sequenceDiagram
    participant Client
    participant GW as API Gateway
    participant JWT as JWT Auth Filter
    participant SVC as Downstream Service

    Client->>GW: Request with Authorization header
    GW->>JWT: Intercept request

    alt Public Path
        JWT->>SVC: Forward directly (no validation)
    else Protected Path
        alt No Auth Header
            JWT-->>Client: 401 Unauthorized
        else Has Bearer Token
            JWT->>JWT: Parse JWT with secret key
            alt Valid Token
                JWT->>JWT: Extract email, role, userId
                JWT->>SVC: Forward with X-User-Email, X-User-Role, X-User-Id headers
                SVC-->>Client: Response
            else Invalid/Expired Token
                JWT-->>Client: 401 Unauthorized
            end
        end
    end
```
