# EduLearn Online Learning Platform — Architecture Diagram

## System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        FE["React Frontend<br/>(EduLearn LMS)"]
    end

    subgraph "API Gateway Layer"
        GW["API Gateway<br/>(Spring Cloud Gateway)<br/>Port: 8080"]
        JWT["JWT Authentication Filter"]
    end

    subgraph "Service Discovery & Config"
        EUR["Eureka Discovery Server<br/>Port: 8761"]
        CFG["Config Server<br/>Port: 8888"]
        REPO["Config Repository<br/>(Git/Local)"]
    end

    subgraph "Microservices Layer"
        AUTH["Auth Service<br/>Port: 8081"]
        USER["User Service<br/>Port: 8083"]
        COURSE["Course Service<br/>Port: 8082"]
        ENROLL["Enrollment Service<br/>Port: 8085"]
        PAY["Payment Service<br/>Port: 8084"]
        NOTIF["Notification Service<br/>Port: 8086"]
    end

    subgraph "Message Broker"
        KFK["Apache Kafka<br/>Port: 9092"]
        ZK["Zookeeper<br/>Port: 2181"]
    end

    subgraph "Data Layer"
        AUTHDB["Auth DB<br/>(MySQL)"]
        USERDB["User DB<br/>(MySQL)"]
        COURSEDB["Course DB<br/>(MySQL)"]
        ENROLLDB["Enrollment DB<br/>(MySQL)"]
        PAYDB["Payment DB<br/>(MySQL)"]
        NOTIFDB["Notification DB<br/>(MySQL)"]
        REDIS["Redis Cache"]
    end

    subgraph "External Services"
        RAZOR["Razorpay API"]
        SMTP["SMTP Email Server"]
        SONAR["SonarQube<br/>Port: 9000"]
    end

    FE -->|HTTP/REST| GW
    GW --> JWT
    JWT -->|Validated Request| AUTH
    JWT -->|Validated Request| USER
    JWT -->|Validated Request| COURSE
    JWT -->|Validated Request| ENROLL
    JWT -->|Validated Request| PAY
    JWT -->|Validated Request| NOTIF

    AUTH --> EUR
    USER --> EUR
    COURSE --> EUR
    ENROLL --> EUR
    PAY --> EUR
    NOTIF --> EUR

    CFG --> REPO
    AUTH --> CFG
    USER --> CFG
    COURSE --> CFG

    AUTH -->|Produce| KFK
    COURSE -->|Produce| KFK
    KFK -->|Consume| NOTIF
    KFK -->|Consume| USER
    KFK --> ZK

    AUTH --> AUTHDB
    USER --> USERDB
    COURSE --> COURSEDB
    ENROLL --> ENROLLDB
    PAY --> PAYDB
    NOTIF --> NOTIFDB
    PAY --> REDIS

    PAY --> RAZOR
    AUTH --> SMTP
    NOTIF --> SMTP
```

## Communication Patterns

| Pattern | Source | Destination | Description |
|---------|--------|-------------|-------------|
| Sync REST | Frontend | API Gateway | All client requests |
| Sync REST | API Gateway | Microservices | Routed via Eureka |
| Async Kafka | Auth Service | Notification Service | Welcome/Login emails |
| Async Kafka | Auth Service | User Service | Profile sync |
| Async Kafka | Course Service | Notification Service | Course announcements |
| Sync REST | Payment Service | Razorpay API | Payment processing |
| Sync REST | Payment Service | Enrollment Service | Mark enrollment active |

## Port Mapping

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Auth Service | 8081 |
| Course Service | 8082 |
| User Service | 8083 |
| Payment Service | 8084 |
| Enrollment Service | 8085 |
| Notification Service | 8086 |
| Eureka Discovery | 8761 |
| Config Server | 8888 |
| Kafka | 9092 |
| SonarQube | 9000 |
| Redis | 6379 |
