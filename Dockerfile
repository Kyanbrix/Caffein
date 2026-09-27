# Stage 1: Build the bot using the official Maven image
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN apt-get update && \
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

COPY src/main/resources/package.json src/main/resources/package-lock.json ./
RUN npm install && npx playwright install --with-deps chromium

RUN groupadd -r botgroup && useradd -r -g botgroup botuser

COPY --from=builder --chown=botuser:botgroup /app/target/Caffeine.jar ./bot.jar
COPY --chown=botuser:botgroup src/main/resources/render-confession.js ./render-confession.js
COPY --chown=botuser:botgroup src/main/resources/render-charpage.js ./render-charpage.js

RUN chown -R botuser:botgroup /ms-playwright

USER botuser

CMD ["java", "-jar", "bot.jar"]