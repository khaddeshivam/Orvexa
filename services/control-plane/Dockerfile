FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 orvexa
COPY --from=build /workspace/target/control-plane-0.1.0-SNAPSHOT.jar app.jar
RUN chown -R orvexa:orvexa /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
