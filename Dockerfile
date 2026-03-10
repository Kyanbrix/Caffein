# Stage 1: Build the bot using the official Maven image
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy ONLY the pom.xml first to cache dependencies
COPY pom.xml .

# Download dependencies (no wrapper needed!)
RUN mvn dependency:go-offline

# Copy the actual code
COPY src ./src

# Build the fat jar
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN addgroup -S botgroup && adduser -S botuser -G botgroup

COPY --from=builder --chown=botuser:botgroup /app/target/Caffeine.jar ./bot.jar

USER botuser

CMD ["java", "-jar", "bot.jar"]