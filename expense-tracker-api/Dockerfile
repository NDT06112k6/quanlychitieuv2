FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/target/expense-tracker-api.jar ./app.jar

USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
