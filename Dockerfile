FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/cafe.jar /app/cafe.jar

=ENV DISCORD_TOKEN=""\
    DB_PASSWORD=""\
    DB_URL="" \
    DB_USER=""/


CMD ["java", "-jar", "/app/cafe.jar"]