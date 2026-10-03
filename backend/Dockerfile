FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
RUN groupadd --gid 10001 expense && useradd --uid 10001 --gid expense --create-home expense
WORKDIR /app
COPY --from=build /build/target/expense-platform-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data/attachments && chown -R expense:expense /app
USER expense
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
