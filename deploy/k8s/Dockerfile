FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --chown=10001:10001 springai-demo-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
