FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 aiops && mkdir -p /app/reports /app/skills /app/runbooks && chown -R aiops:aiops /app
COPY --from=build /workspace/target/aiops-agent-platform-*.jar /app/app.jar
COPY --chown=aiops:aiops skills /app/skills
COPY --chown=aiops:aiops runbooks /app/runbooks
USER aiops
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

