FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp verify
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 commerce && useradd --uid 10001 --gid commerce --no-create-home commerce
WORKDIR /app
COPY --from=build --chown=commerce:commerce /build/target/*.jar app.jar
USER 10001:10001
EXPOSE 8888
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

