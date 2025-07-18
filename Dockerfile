FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/proxy-client-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
