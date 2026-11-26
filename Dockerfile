# syntax=docker/dockerfile:1

# ---- Stage 1: build the fat jar ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
# The cache mount keeps ~/.m2 between builds, so dependencies are only downloaded once.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# ---- Stage 2: install the renderer's npm packages ----
FROM node:22-bookworm-slim AS node
WORKDIR /deps
COPY src/main/resources/package.json src/main/resources/package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre-noble AS runner
WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    # glibc otherwise gives each busy thread its own 64 MB malloc arena, which inflates RSS.
    MALLOC_ARENA_MAX=2 \
    # Serial GC has the smallest footprint and returns unused heap to the OS.
    # The heap cap leaves room for Node + Chromium in the same container.
    # Override in compose (environment: JAVA_TOOL_OPTIONS=...) if needed.
    JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=40 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError"

# Only the node binary is needed at runtime (no npm, no NodeSource repo).
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /deps/node_modules ./node_modules

# chromium.launch() runs headless, which uses chromium-headless-shell. --only-shell
# skips the full Chromium build that was never launched. tini reaps the Chromium
# processes left behind when a timed-out renderer is killed.
RUN apt-get update \
 && apt-get install -y --no-install-recommends tini \
 && node node_modules/playwright/cli.js install --with-deps --only-shell chromium \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/* /tmp/*

RUN groupadd -r botgroup && useradd -r -g botgroup botuser

# Owned by root and only readable by the bot, which never writes to /app.
COPY --from=builder /app/target/Caffeine.jar ./bot.jar
COPY src/main/resources/render-confession.js src/main/resources/render-charpage.js ./

USER botuser

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["java", "-jar", "bot.jar"]
