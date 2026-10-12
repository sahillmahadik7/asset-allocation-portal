
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/asset-allocation-portal-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar", "--server.port=8081"]
