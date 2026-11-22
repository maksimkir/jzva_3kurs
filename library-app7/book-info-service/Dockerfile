# ЛР7 — образ book-info-service (порт 8082).
# Спочатку зібрати jar з кореня проєкту:  mvn -pl book-info-service -am package -DskipTests
# Потім:  docker build -t library/book-info-service book-info-service
FROM eclipse-temurin:17-jre
WORKDIR /app
# виконуваний jar від spring-boot-maven-plugin (*.jar.original під маску не потрапляє)
COPY target/book-info-service-*.jar app.jar
EXPOSE 8082
# процес не від root
RUN useradd --system --uid 1001 app
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
