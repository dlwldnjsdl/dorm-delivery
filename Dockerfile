FROM node:24-bookworm-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/index.html frontend/vite.config.js ./
COPY frontend/src ./src
COPY frontend/public ./public
COPY frontend/tests ./tests
RUN npm test && npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=backend --chown=app:app /build/target/dorm-delivery-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=demo
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
