FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY zflow-api/pom.xml zflow-api/pom.xml
COPY zflow-app/pom.xml zflow-app/pom.xml
COPY zflow-domain/pom.xml zflow-domain/pom.xml
COPY zflow-infrastructure/pom.xml zflow-infrastructure/pom.xml
COPY zflow-trigger/pom.xml zflow-trigger/pom.xml
COPY zflow-types/pom.xml zflow-types/pom.xml
COPY zflow-api/src zflow-api/src
COPY zflow-app/src zflow-app/src
COPY zflow-domain/src zflow-domain/src
COPY zflow-infrastructure/src zflow-infrastructure/src
COPY zflow-trigger/src zflow-trigger/src
COPY zflow-types/src zflow-types/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S miniagent && adduser -S miniagent -G miniagent && chown miniagent:miniagent /app
COPY --from=build --chown=miniagent:miniagent /workspace/zflow-app/target/zflow-app.jar /app/app.jar
USER miniagent
EXPOSE 8066
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
