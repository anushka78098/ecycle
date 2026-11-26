# Build from the repository root: docker build -f backend/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY shared/pom.xml shared/
COPY backend/pom.xml backend/
COPY frontend/pom.xml frontend/
COPY shared shared
COPY backend backend
RUN mvn -q -pl backend -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 ecycle && mkdir -p /app/data/uploads && chown -R ecycle /app
WORKDIR /app
COPY --from=build /src/backend/target/ecycle-backend-*.jar app.jar
USER ecycle
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
