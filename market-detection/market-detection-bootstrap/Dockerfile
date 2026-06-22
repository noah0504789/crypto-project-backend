FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8500

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
