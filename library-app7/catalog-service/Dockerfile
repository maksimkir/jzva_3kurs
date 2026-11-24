# ЛР7 — образ catalog-service (порт 8080).
# Спочатку зібрати jar з кореня проєкту:  mvn -pl catalog-service -am package -DskipTests
# Потім:  docker build -t library/catalog-service catalog-service
FROM eclipse-temurin:17-jre
WORKDIR /app
# виконуваний jar від spring-boot-maven-plugin (*.jar.original під маску не потрапляє)
COPY target/catalog-service-*.jar app.jar
EXPOSE 8080
# процес не від root
RUN useradd --system --uid 1001 app
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
