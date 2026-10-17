FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 financehub && adduser -S -u 10001 financehub -G financehub
WORKDIR /app
COPY --from=build --chown=financehub:financehub /build/target/financehub-0.1.0-SNAPSHOT.jar app.jar
USER financehub
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
