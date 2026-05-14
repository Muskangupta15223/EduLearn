# LMS Architecture And Gap Analysis

## Scope And Evidence

This review is based on:

- The LMS case-study screenshots you provided, especially the actor list, use-case summary, instructor requirements, and target microservice list.
- The current backend implementation under `backend/`.
- The current React frontend under `edulearn-vite/`.
- Existing internal project docs such as `backend/docs/UseCase_Component_Deployment_Diagrams.md`.

Important constraint:

- I could not automatically extract the full PDF text in this environment because no local PDF text tool or Python PDF library is installed. The analysis below uses the screenshots plus the codebase itself, which is still enough to validate the major requirements you highlighted.

## 1. Requirement Validation

### 1.1 Actors Required By The Case Study

| Actor | Expected Responsibilities | Current Coverage |
|---|---|---|
| Guest User | Browse catalog, search, preview free lessons, view course details, register/login | Partial |
| Student | Purchase course, subscribe, watch lessons, take quizzes, track progress, download certificate, join forum | Partial |
| Instructor | Maintain profile, create/edit/delete course, add lessons/resources, create quizzes, publish/unpublish, view student progress, moderate forum, analytics | Partial |
| Admin | Manage users, approve/reject courses, view enrollments/payments/certificates/analytics, platform notifications, moderation | Partial |
| System | Notifications, certificate issuance, progress computation | Partial |
| Payment Gateway | Process course purchase and subscriptions | Partial |

### 1.2 Target Use Cases From The Case Study

| Use Case | Status | Evidence |
|---|---|---|
| Register / Login | Implemented | `auth-service` supports register/login and Google OAuth2 |
| Browse / Search Courses | Implemented | `course-service` has published list, category, search endpoints |
| Preview Free Lessons | Missing end-to-end | Lesson model has `isFreePreview`, but guest lesson-access flow is not enforced or exposed properly |
| View Course Details | Implemented | Public course fetch exists |
| Purchase Course | Implemented but fragile | Razorpay order + verify flow exists, but consistency and webhook flow are incomplete |
| Subscribe | Missing | Frontend has placeholder API methods only; no backend subscription service or controller |
| Watch Lessons | Partial | Course player exists; lesson content exists; no robust access policy for preview vs paid content |
| Take Quiz | Implemented but partial | Quiz CRUD and attempts work; no time limits, max attempts, or question type diversity |
| Track Progress | Partial | Enrollment progress exists, but only course-level percent and weak lesson semantics |
| Download Certificate | Missing | Frontend expects endpoint; backend does not provide certificate issuance/download |
| Post / Reply In Forum | Implemented but partial | Thread/reply flow exists; moderation features missing |
| Create Course | Implemented | Instructor CRUD exists |
| Add Lessons / Resources | Partial | Lessons exist; resource attachments and media upload endpoints are missing/incomplete |
| Create Quiz | Partial | Supported, but only basic MCQ-style model |
| Publish Course | Partial | Submit-for-approval exists; unpublish and reject are missing |
| View Student Progress | Implemented but basic | Instructor dashboard pulls enrollments by course |
| Manage Users | Partial | Role update and list exist; suspend/delete are missing |
| Approve / Reject Course | Partial | Approve exists; reject endpoint is missing |
| View Platform Analytics | Partial | Simple derived dashboard exists; no dedicated analytics model/service |
| Send Notification | Partial | Notification CRUD and Kafka consumers exist; broadcast is simplistic |

### 1.3 Target Microservices In The Case Study vs Current Implementation

#### Expected in case study

- `auth-service`
- `course-service`
- `lesson-service`
- `enrollment-service`
- `assessment-service`
- `payment-service`
- `progress-service`
- `discussion-service`
- `notification-service`
- `edulearn-web`

#### Actually present in this codebase

- `auth-service`
- `user-service`
- `course-service`
- `enrollment-service`
- `payment-service`
- `notification-service`
- infrastructure: `api-gateway`, `discovery-service`, `config-service`, `admin-server`
- frontend: `edulearn-vite`

#### Responsibility mapping

| Expected Service | Current Equivalent | Assessment |
|---|---|---|
| Auth | `auth-service` | Good baseline |
| Course Catalog | `course-service` | Present |
| Lesson | Inside `course-service` | Acceptable for MVP, not aligned with target service split |
| Assessment | Inside `course-service` | Acceptable for MVP, but heavy domain aggregation |
| Progress | Inside `enrollment-service` | Too simplified for case-study scope |
| Discussion | Inside `course-service` | Works, but not isolated |
| Payment | `payment-service` | Present, but needs hardening |
| Notification | `notification-service` | Present |
| User/Profile | `user-service` | Extra service not explicitly shown in case study, but useful |

## 2. Implemented / Partial / Missing Feature Summary

### Fully implemented

- JWT-based login for local users
- Google OAuth2 login
- User profile creation via Kafka signup event
- Course CRUD for instructors
- Course search and published catalog browsing
- Module and lesson creation
- Quiz creation, questions, attempts, auto-scoring
- Assignment creation and grading basics
- Enrollment creation and enrollment checks
- Notification persistence and unread count
- API gateway, service discovery, config server
- Redis usage for payment locking

### Partially implemented

- Payment purchase reliability
- Instructor dashboard
- Admin dashboard
- Course approval workflow
- Discussion forum
- Progress tracking
- Lesson access control
- Instructor analytics
- Password reset
- Email notifications
- File uploads

### Missing

- Subscription plans and recurring billing
- Refund flow
- Payment webhooks with signature verification
- Certificate generation and download
- Course rejection flow
- Course unpublish flow
- User suspension / deletion
- Instructor verification with government ID
- Best reply, pin/close/delete moderation actions
- Lesson reordering APIs
- Lesson resource attachments
- Timed quizzes
- Quiz max attempts
- Multi-question types beyond fixed A/B/C/D
- Real analytics pipeline
- Real-time updates over WebSocket/SSE
- Audit trails and admin review history

## 3. Architecture Review

### 3.1 Service Boundaries

Current strengths:

- Auth, user profile, payment, enrollment, and notification are separated.
- Each main service appears to own its own MySQL schema.
- API gateway, Eureka, and config server are present, which is a strong base for distributed deployment.

Current weaknesses:

- `course-service` is overloaded. It currently owns:
  - course catalog
  - modules
  - lessons
  - quizzes
  - questions
  - assignments
  - submissions
  - discussions
- `enrollment-service` is also acting as a simplified progress engine.
- The actual implementation is narrower than the case-study target architecture, so the service boundaries do not yet match the intended design.

Recommendation:

- Keep the current split for now if delivery speed matters.
- For production maturity, extract:
  - `assessment-service` from `course-service`
  - `discussion-service` from `course-service`
  - `progress-service` from `enrollment-service`
- Keep `lesson-service` separate only if you need independent media scaling, DRM, transcoding, or CDN-heavy workloads.

### 3.2 Inter-service Communication

Current state:

- REST is used for synchronous lookups and commands.
- Kafka is used for signup and enrollment-triggered notifications.
- Payment-to-enrollment activation is still REST-coupled.

Issues:

- Cross-service write flow is not transactionally safe.
- No outbox pattern is used before publishing Kafka events.
- Events are thin and inconsistent.
- Some flows swallow downstream failures silently.

Recommendations:

- Use REST only for query-style dependencies and user-blocking orchestration.
- Use Kafka domain events for:
  - `PaymentAuthorized`
  - `PaymentCaptured`
  - `PaymentFailed`
  - `EnrollmentActivated`
  - `CourseSubmittedForReview`
  - `CourseApproved`
  - `CourseRejected`
  - `CoursePublished`
  - `CourseCompleted`
  - `CertificateIssued`
  - `InstructorVerificationSubmitted`
  - `InstructorVerificationApproved`
  - `InstructorVerificationRejected`
- Add the transactional outbox pattern in `payment-service`, `course-service`, and `enrollment-service`.

### 3.3 Database Per Service

This is one of the stronger areas.

- The project is already organized around separate databases per service.
- That aligns with good microservice isolation.

Gaps:

- No explicit read-model or reporting store exists.
- Admin analytics are computed ad hoc from operational APIs.
- No audit/event tables exist for compliance-sensitive flows such as payments and verification.

Recommendations:

- Keep separate write databases.
- Add a reporting database or materialized analytics store for dashboards.
- Add audit tables for admin actions, payment state transitions, and instructor verification reviews.

### 3.4 Redis Usage

Current state:

- Redis is used in `payment-service` for a short idempotency lock and simple cached payment state.

Assessment:

- Good start, but too narrow.

Recommended Redis uses:

- idempotency keys for payment initiation and webhook dedupe
- short-lived payment session state
- password-reset token storage instead of in-memory map
- rate limiting for auth endpoints
- cache for popular course lists and course-detail read models
- notification unread counters
- optional session / refresh-token revocation lists

### 3.5 Security Review

What is good:

- JWT generation exists.
- Gateway forwards identity headers after token parsing.
- OAuth2 login exists.

Critical gaps:

- Gateway allows many protected calls to pass through with no token; downstream services often rely on missing-header defaults.
- Multiple controllers use `defaultValue = "1"` or `defaultValue = "STUDENT"`, which can accidentally authorize anonymous requests.
- Password reset tokens are kept in memory and are not expiring robustly.
- No refresh-token strategy.
- No per-endpoint role policy at gateway or service level.
- No object-level authorization for many admin operations.

Recommendations:

- Reject missing or invalid JWTs at the gateway for non-public routes.
- Remove all default user-id / role fallbacks from controllers.
- Store password reset tokens in Redis with TTL.
- Add refresh tokens with rotation and revocation.
- Consider Spring Authorization Server or external IdP if the platform grows.
- Add method-level authorization annotations and ownership checks consistently.

## 4. Functional Gap Analysis By Use Case

### Register / Login

Current:

- Local register/login works.
- Google OAuth2 works.
- Password reset exists but uses in-memory token storage.

Gap:

- No refresh token, lockout, email verification, or account status checks.

Implementation target:

- Add `refresh_tokens` table or Redis-backed rotating refresh tokens.
- Add `account_status` in auth/user profile.
- Add `email_verified` flow.

### Browse / Search Courses

Current:

- Public published list, featured list, category filter, and search exist.

Gap:

- No advanced filtering by instructor, language, price, rating, or level combinations.

Implementation target:

- Backend query params:
  - `GET /courses/published?category=&level=&language=&priceType=&instructor=&q=`
- Add indexed fields: `language`, `status`, `level`, `category`, `price`.

### Preview Free Lessons

Current:

- Lesson entity contains `isFreePreview`.
- There is no guest-safe lesson-content endpoint and no robust access rule.

Gap:

- The feature exists in data shape, not as a secure end-to-end use case.

Implementation target:

- Add endpoint `GET /courses/{courseId}/preview`
- Return only free-preview lessons and sanitized content metadata.
- Enforce:
  - guest -> only `isFreePreview=true`
  - enrolled student -> full content
  - instructor/admin -> full content

### Purchase Course

Current:

- Frontend checkout page is implemented.
- Razorpay order creation exists.
- Signature verification exists.
- Enrollment activation is attempted after successful verification.

Gap:

- Reliability is not production-ready.
- See payment section for full details.

### Subscribe

Current:

- React API client has placeholder methods for subscriptions.

Gap:

- No backend subscription domain exists.

Implementation target:

- Add either:
  - recurring plan support in `payment-service`, or
  - separate `subscription-service`
- Required tables:
  - `subscription_plans`
  - `subscriptions`
  - `subscription_invoices`
  - `payment_attempts`

### Watch Lessons

Current:

- Course player UI exists.
- Lessons can contain text or video URL.

Gap:

- No proper lesson access policy.
- No lesson completion persistence.
- No media upload/transcoding/CDN support.

Implementation target:

- Add `lesson_progress` table in `progress-service`.
- Persist:
  - `started_at`
  - `completed_at`
  - `percent_watched`
  - `last_position_seconds`

### Take Quiz

Current:

- Students can start attempts and submit answers.
- Score and pass/fail are computed.

Gap:

- No time limits enforced server-side.
- No max attempts.
- No per-question type model.
- No anti-repeat strategy.

Implementation target:

- Add fields on `quizzes`:
  - `time_limit_minutes`
  - `max_attempts`
  - `shuffle_questions`
  - `shuffle_options`
- Add question types:
  - `MCQ_SINGLE`
  - `TRUE_FALSE`
  - `SHORT_TEXT`

### Track Progress

Current:

- A single course-level percent is stored on enrollment.

Gap:

- This does not satisfy lesson-level progress from the case study.

Implementation target:

- Separate `progress-service`
- Tables:
  - `lesson_progress`
  - `course_progress`
  - `completion_events`

### Download Certificate

Current:

- Frontend expects it.
- Backend does not provide it.

Implementation target:

- On course completion:
  - emit `CourseCompleted`
  - certificate service or progress service generates PDF
  - persist certificate record
- Endpoint:
  - `GET /certificates/course/{courseId}/me`
- Schema:

```sql
CREATE TABLE certificates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  certificate_no VARCHAR(64) NOT NULL UNIQUE,
  pdf_url VARCHAR(512) NOT NULL,
  verification_code VARCHAR(64) NOT NULL UNIQUE,
  issued_at TIMESTAMP NOT NULL,
  UNIQUE KEY uk_user_course (user_id, course_id)
);
```

### Post / Reply In Forum

Current:

- Thread and reply creation exist.

Gap:

- No pin, close, best reply, or moderation audit.

Implementation target:

- Add fields:
  - thread: `is_pinned`, `is_closed`, `best_reply_id`
  - reply: `upvote_count`, `is_best_reply`

### Create Course / Add Lessons / Resources / Create Quiz / Publish

Current:

- Course/module/lesson/quiz flows are mostly present.

Gaps:

- No lesson attachments/resources flow.
- No reorder APIs.
- No reject/unpublish flow.
- No instructor verification check before publishing.

Implementation target:

- Block publish if instructor not verified.
- Add resource table:

```sql
CREATE TABLE lesson_resources (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lesson_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(512) NOT NULL,
  mime_type VARCHAR(128),
  size_bytes BIGINT,
  created_at TIMESTAMP NOT NULL
);
```

### Manage Users / Approve Courses / Platform Analytics / Send Notifications

Current:

- Admin can list users and update roles.
- Admin can approve courses.
- Admin dashboards derive basic metrics.
- Notification send/broadcast endpoints exist.

Gaps:

- No suspend/delete user endpoints.
- No course rejection reason tracking.
- No payment analytics source of truth.
- Broadcast is not a real fan-out.

Implementation target:

- Add user fields:
  - `account_status` with `ACTIVE`, `SUSPENDED`, `DELETED`
- Add course review fields:
  - `review_status`
  - `reviewed_by`
  - `reviewed_at`
  - `review_comment`

## 5. Critical Payment Review

### 5.1 What Exists Today

- Razorpay order creation endpoint
- Razorpay client integration
- signature verification endpoint
- payment transaction table
- Redis lock for idempotency in one flow
- payment history endpoint
- enrollment activation after successful verification

### 5.2 Critical Issues

#### 1. Payment status model is inconsistent

- `createPayment()` marks payments `SUCCESS` immediately in a simulated flow.
- Razorpay order flow uses `PROCESSING` then `SUCCESS`.
- Case-study-required status flow should be explicit:
  - `PENDING`
  - `AUTHORIZED`
  - `CAPTURED` or `SUCCESS`
  - `FAILED`
  - `REFUND_PENDING`
  - `REFUNDED`
  - `CANCELLED`

#### 2. Webhook handling is not production-ready

- `/payments/razorpay/webhook` simply calls generic `processWebhook()`.
- There is no webhook signature verification.
- No event dedupe is present.
- No raw payload persistence exists.

#### 3. Idempotency is incomplete

- Generic `createPayment()` supports `Idempotency-Key`.
- Razorpay order creation does not persist or enforce the caller-provided idempotency key.
- Duplicate browser retries can still create inconsistent transaction records.

#### 4. Enrollment activation is tightly coupled and fragile

- Payment verification directly calls enrollment creation through REST.
- If enrollment activation fails after payment capture, the user can be charged without receiving access.

#### 5. Refunds are missing

- Frontend exposes refund APIs.
- Backend has no refund endpoint or refund domain logic.

#### 6. Subscriptions are missing

- Frontend has subscription methods.
- Backend has no subscription controller, storage, or webhook logic.

#### 7. Security concerns

- `getMyPayments()` trusts forwarded `X-User-Id`, but the gateway currently does not strictly block missing tokens.

### 5.3 Production-Ready Payment Flow

#### Recommended tables

```sql
CREATE TABLE payment_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  course_id BIGINT NULL,
  order_type VARCHAR(32) NOT NULL,
  order_ref VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(8) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_order_id VARCHAR(128) UNIQUE,
  provider_payment_id VARCHAR(128) UNIQUE,
  provider_signature VARCHAR(256),
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  failure_code VARCHAR(64),
  failure_reason VARCHAR(512),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_source VARCHAR(32) NOT NULL,
  provider_event_id VARCHAR(128),
  payload_json JSON NOT NULL,
  processed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE refunds (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id BIGINT NOT NULL,
  provider_refund_id VARCHAR(128) UNIQUE,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(255),
  requested_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

#### Recommended synchronous flow

1. Student clicks buy course.
2. Frontend sends `POST /payments/orders` with idempotency key.
3. Payment service:
   - validates course price from course service
   - checks no prior successful entitlement
   - creates local transaction as `PENDING`
   - creates provider order
   - stores `provider_order_id`
   - returns checkout payload
4. Frontend opens Razorpay checkout.
5. Frontend sends `POST /payments/verify` after callback.
6. Payment service:
   - verifies signature
   - marks transaction `AUTHORIZED` or `SUCCESS`
   - writes outbox event `PaymentCaptured`
7. Enrollment service consumes `PaymentCaptured` and activates access.
8. Enrollment service emits `EnrollmentActivated`.
9. Notification service consumes and notifies user and instructor.

#### Webhook flow

1. Provider sends webhook.
2. Verify webhook signature.
3. Persist raw event with provider event id.
4. Ignore duplicate webhook if already processed.
5. Update transaction state machine only through valid transitions.
6. Emit domain event through outbox.

#### Retry and compensation

- If payment succeeds but enrollment fails:
  - transaction remains `SUCCESS_PENDING_FULFILLMENT`
  - retry consumer attempts enrollment activation
  - after retry exhaustion, mark `FULFILLMENT_FAILED`
  - create support/admin alert
- Refund only after confirmed failure or admin action.

#### Kafka events for payment

- `PaymentInitiated`
- `PaymentAuthorized`
- `PaymentCaptured`
- `PaymentFailed`
- `PaymentRefundRequested`
- `PaymentRefunded`

## 6. New Requirement: Instructor Verification With Government ID

### 6.1 Business Rule

- Instructors may create draft courses before verification.
- Only verified instructors can submit a course for approval or publish it.
- Admin reviews government ID and either approves or rejects verification.

### 6.2 Suggested Service Ownership

Best fit: extend `user-service`.

Why:

- verification belongs to identity/profile and admin review
- keeps instructor trust state next to user profile
- avoids putting KYC-style data in `course-service`

### 6.3 Database Schema

```sql
ALTER TABLE user_profiles
  ADD COLUMN expertise_areas VARCHAR(255) NULL,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
  ADD COLUMN verification_notes VARCHAR(512) NULL,
  ADD COLUMN verified_at TIMESTAMP NULL,
  ADD COLUMN verified_by BIGINT NULL,
  ADD COLUMN publishing_blocked BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE instructor_verification_documents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(512) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  document_type VARCHAR(64) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  uploaded_at TIMESTAMP NOT NULL,
  review_status VARCHAR(32) NOT NULL,
  reviewed_by BIGINT NULL,
  reviewed_at TIMESTAMP NULL,
  rejection_reason VARCHAR(512) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE instructor_verification_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  action_by BIGINT NOT NULL,
  notes VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL
);
```

### 6.4 Backend API Design

#### Instructor APIs

- `POST /users/{id}/verification/document`
  - multipart upload
  - allowed types: `image/jpeg`, `image/png`, `application/pdf`
- `GET /users/{id}/verification`
  - current verification status and latest review result

#### Admin APIs

- `GET /admin/instructor-verifications?status=PENDING`
- `GET /admin/instructor-verifications/{userId}`
- `PUT /admin/instructor-verifications/{userId}/approve`
- `PUT /admin/instructor-verifications/{userId}/reject`

#### Example response model

```json
{
  "userId": 42,
  "verificationStatus": "PENDING",
  "publishingBlocked": true,
  "document": {
    "id": 99,
    "fileUrl": "/secure-files/instructor-verification/99",
    "documentType": "GOVERNMENT_ID",
    "uploadedAt": "2026-05-05T10:00:00Z"
  },
  "reviewedAt": null,
  "reviewedBy": null,
  "rejectionReason": null
}
```

### 6.5 File Storage Approach

Recommended:

- store binary file in object storage:
  - AWS S3
  - Cloudinary private asset
  - Azure Blob
  - MinIO for local/dev
- keep only metadata in MySQL
- serve via signed URLs or secured proxy endpoint

For local development:

- continue local filesystem storage
- but store under dedicated path such as:
  - `uploads/instructor-verification/{userId}/...`

Do not:

- keep sensitive ID documents publicly accessible under static upload URLs

### 6.6 Publishing Gate

In `course-service.publishCourse(...)`:

- call `user-service` to verify instructor status, or consume a replicated event/read model
- reject publish when instructor is not `APPROVED`

### 6.7 Kafka Events

- `InstructorVerificationSubmitted`
- `InstructorVerificationApproved`
- `InstructorVerificationRejected`

Consumers:

- notification-service -> informs instructor
- course-service -> updates local publish eligibility cache if needed

### 6.8 Admin Dashboard UI

Add page:

- `AdminInstructorVerifications.jsx`

UI sections:

- pending queue
- review drawer/modal
- document preview
- approve/reject controls
- audit timeline

Suggested columns:

- instructor name
- email
- uploaded at
- status
- document type
- action buttons

### 6.9 Instructor UI

Add page:

- `InstructorVerification.jsx`

States:

- `NOT_SUBMITTED`
- `PENDING`
- `APPROVED`
- `REJECTED`

Behavior:

- upload form when not submitted or rejected
- warning banner in instructor dashboard when not approved
- disable publish CTA until verified

## 7. Frontend Review And Improvement Plan

### 7.1 Current Frontend Strengths

- Good route structure by role
- React Query usage is a strong choice
- Framer Motion is already present
- Role-based dashboards exist
- Payment page and instructor page are visually richer than raw CRUD screens

### 7.2 Current Frontend Gaps

- Some APIs are placeholders for backend routes that do not exist.
- Several screens assume data fields that backend does not provide.
- There is no normalized state for auth/permissions beyond context and local storage.
- Real-time notifications are not implemented.
- No robust loading skeleton pattern across all pages.
- Some admin pages ship fake `initialData`, which can hide backend gaps.

### 7.3 Suggested Component Structure

```text
src/
  app/
    router/
    providers/
    store/
  features/
    auth/
    catalog/
    course-player/
    instructor-courses/
    discussions/
    assessments/
    payments/
    subscriptions/
    notifications/
    admin-users/
    admin-course-review/
    admin-instructor-verification/
  components/
    ui/
    forms/
    feedback/
    charts/
```

### 7.4 State Management

Recommendation:

- keep server state in React Query
- use Zustand for lightweight client state:
  - sidebar
  - filters
  - current lesson player state
  - payment modal state
  - websocket connection state

### 7.5 API Integration Strategy

- Maintain one `services/api.js` split into feature modules instead of a single very large file.
- Add runtime contract checks with Zod on critical responses.
- Remove placeholder API methods until backend exists, or clearly feature-flag them.

### 7.6 UX Improvements By Persona

#### Student

- real enrollment state badge on course cards
- resume lesson CTA
- inline progress ring per course
- quiz countdown and attempt history
- certificate card after completion

#### Instructor

- verification status banner
- curriculum drag-and-drop ordering
- publish readiness checklist
- quiz/question bulk editor
- review feedback from admin when rejected

#### Admin

- moderation inbox
- payment dispute/refund queue
- instructor verification queue
- analytics with date filters and drill-down

## 8. Animations And Real-Time UX

### Recommended UX additions

- page skeletons instead of blank spinners
- optimistic updates for read/unread notifications
- staggered list entrances for dashboards
- payment state modal with live status polling
- toasts tied to domain events

### Real-time approach

Best practical path:

- backend notification-service publishes notification-created events
- gateway exposes WebSocket or SSE endpoint
- frontend subscribes for:
  - new notifications
  - payment updates
  - instructor verification decision
  - admin moderation queue changes

## 9. Concrete High-Priority Backend Work

### Priority 1

- Enforce authentication at gateway
- Remove default user-id/role controller fallbacks
- Harden payment flow with verified webhooks and idempotency
- Implement course reject/unpublish
- Implement certificate generation
- Implement instructor verification

### Priority 2

- Add lesson-resource uploads
- Add quiz time limit and max attempts
- Add password reset token storage in Redis
- Add admin suspend/delete user
- Add event outbox pattern

### Priority 3

- Extract `progress-service`
- Extract `discussion-service`
- Add subscriptions
- Add reporting store
- Add WebSocket/SSE real-time layer

## 10. Concrete High-Priority Frontend Work

### Priority 1

- Remove placeholder calls to missing endpoints or implement those endpoints
- Add instructor verification screens
- Add reject-course admin flow
- Add certificate view/download flow
- Add payment-status states and receipt/refund UI only when supported

### Priority 2

- Improve course player progress logic
- Add curriculum ordering UI
- Add discussion moderation UI
- Add responsive admin moderation tables/cards

### Priority 3

- Real-time notifications
- subscription checkout
- advanced analytics screens

## 11. Recommended Step-By-Step Implementation Plan

1. Secure the platform foundation.
   - Lock down gateway auth behavior.
   - remove controller default identities
   - move password-reset tokens to Redis

2. Normalize course moderation.
   - add course reject/unpublish endpoints
   - store review reason and reviewer metadata
   - wire admin UI fully

3. Add instructor verification.
   - extend `user-service` schema
   - add secure document upload and review APIs
   - block unverified instructors from publishing
   - add instructor/admin UI

4. Harden payments.
   - introduce real payment state machine
   - verify Razorpay webhooks
   - dedupe provider events
   - add refund API
   - emit Kafka payment events through outbox

5. Fix progress and completion.
   - add lesson-level progress tracking
   - compute course completion deterministically
   - generate certificates

6. Close content gaps.
   - lesson resources
   - timed quizzes
   - attempt limits
   - forum moderation actions

7. Improve operational maturity.
   - circuit breakers and retries
   - dead-letter topics
   - observability and audit logs
   - reporting store for analytics

## 12. Bottom Line

The project already has a solid microservice starter foundation and a much stronger frontend than a typical student CRUD LMS project. The biggest gap is not breadth of features alone; it is end-to-end reliability and policy enforcement.

The most important work to make this production-ready is:

- secure the gateway and service authorization behavior
- harden payment consistency and webhook processing
- implement instructor verification and publish gating
- add certificate issuance and lesson-level progress
- finish the moderation and admin workflows that are currently only partial
