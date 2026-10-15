# Runtime smoke only: package first with JDK21 Maven verify.
# The production multi-stage build remains docker/Dockerfile.knowledge.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY backend/knowledge-service/target/knowledge-service-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
