FROM maven:3.9.10-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/cafe.jar /app/cafe.jar

# Render should provide this in environment variables.
ENV DISCORD_TOKEN=""

CMD ["java", "-jar", "/app/cafe.jar"]
