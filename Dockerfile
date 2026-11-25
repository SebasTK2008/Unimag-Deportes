FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/target/demo-0.0.1-SNAPSHOT.jar app.jar
COPY docker/healthcheck.sh /app/healthcheck.sh
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 CMD ["bash", "/app/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "app.jar"]
