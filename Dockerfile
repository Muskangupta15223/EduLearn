FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

ARG SERVICE_NAME

# Copy pom files first for caching
COPY pom.xml ./

COPY discovery-service/pom.xml ./discovery-service/
COPY config-service/pom.xml ./config-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY auth-service/pom.xml ./auth-service/
COPY user-service/pom.xml ./user-service/
COPY course-service/pom.xml ./course-service/
COPY enrollment-service/pom.xml ./enrollment-service/
COPY payment-service/pom.xml ./payment-service/
COPY notification-service/pom.xml ./notification-service/
COPY admin-server/pom.xml ./admin-server/

# Cache dependencies
RUN mvn -B dependency:go-offline -DskipTests

# Copy all source code
COPY . .

# Build only target service + dependencies
RUN mvn -B clean package -DskipTests -pl ${SERVICE_NAME} -am

# ============
# Runtime Image
#=============

FROM eclipse-temurin:17-jre-jammy

ARG SERVICE_NAME

RUN groupadd --system spring \
    && useradd --system --gid spring --create-home --shell /usr/sbin/nologin spring

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/${SERVICE_NAME}/target/*.jar /app/app.jar

RUN chown -R spring:spring /app

USER spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]