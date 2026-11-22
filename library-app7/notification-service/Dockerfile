# ЛР7 — образ notification-service (порт 8081).
# Спочатку зібрати jar з кореня проєкту:  mvn -pl notification-service -am package -DskipTests
# Потім:  docker build -t library/notification-service notification-service
FROM eclipse-temurin:17-jre
WORKDIR /app
# виконуваний jar від spring-boot-maven-plugin (*.jar.original під маску не потрапляє)
COPY target/notification-service-*.jar app.jar
EXPOSE 8081
# процес не від root
RUN useradd --system --uid 1001 app
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
