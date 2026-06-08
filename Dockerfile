FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/news-fetch-service-*.jar /app/news-fetch-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/news-fetch-service.jar"]
