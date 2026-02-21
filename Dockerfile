FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/cafe.jar /app/cafe.jar

# Railway should provide this in environment variables.
ENV DISCORD_TOKEN=""

CMD ["java", "-jar", "/app/cafe.jar"]
