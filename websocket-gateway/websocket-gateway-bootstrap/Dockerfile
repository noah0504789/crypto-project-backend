FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8100

ENTRYPOINT ["java", "--add-opens=java.base/java.time=ALL-UNNAMED", "-jar", "app.jar"]