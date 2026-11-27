FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/portal.jar app.jar
EXPOSE 8080
# Cap the heap at 15% of RAM (the JVM default is 25%) so the portal leaves room for xray
# on 1 GB servers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=15", "-jar", "app.jar"]
