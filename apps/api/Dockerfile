# Build with: docker build -t hisaab-api apps/api
# Multi-stage: the first stage has the full JDK and Maven to build the jar; the
# final image has only a JRE and the jar, so it is much smaller.

# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy only the files that define dependencies first, so Docker can cache the
# (slow) dependency download until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests need Docker (Testcontainers), so they run in CI, not inside this build.
RUN ./mvnw -B -q -DskipTests package

# ---- Stage 2: run ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Do not run as root inside the container.
RUN useradd --system --no-create-home hisaab
USER hisaab

COPY --from=build /app/target/hisaab-api-*.jar app.jar

EXPOSE 8080

# Heap capped at 300 MB so the whole JVM fits a 512 MB instance (free hosting
# tiers). SPRING_PROFILES_ACTIVE and the DB_* / WEB_ORIGIN variables are
# supplied at run time, never baked into the image.
ENTRYPOINT ["java", "-Xmx300m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
