FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp test package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/nginx-demo-1.0.0.jar /app/nginx-demo.jar
USER appuser
EXPOSE 8002
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/nginx-demo.jar"]
