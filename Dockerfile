# Build stage: compile and package the runnable JAR.
# Tests, the formatting check and SpotBugs run in CI (mvnw verify), not in the image build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY src/main src/main
RUN mvn -B -ntp package -Dmaven.test.skip=true \
    && cp target/gst-invoice-checker-*.jar /gst-invoice-checker.jar

# Runtime stage: a JRE and the JAR, nothing else.
FROM eclipse-temurin:21-jre
COPY --from=build /gst-invoice-checker.jar /app/gst-invoice-checker.jar

# Run as an unprivileged user. Mount your files at /data.
USER 10001:10001
WORKDIR /data

# The tool never uses the network, so it is safe (and recommended) to run it with
# --network none:
#   docker run --rm --network none -v "$PWD:/data" --user "$(id -u):$(id -g)" \
#     gst-invoice-checker check invoices.csv --config gst-checker.yml
ENTRYPOINT ["java", "-jar", "/app/gst-invoice-checker.jar"]
CMD ["--help"]
